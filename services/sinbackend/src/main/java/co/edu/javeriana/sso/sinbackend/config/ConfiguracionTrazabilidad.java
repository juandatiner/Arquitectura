package co.edu.javeriana.sso.sinbackend.config;

import java.util.stream.Stream;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.config.AbstractRabbitListenerContainerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/** Propaga el correlationId fuera del hilo de una petición HTTP entrante. */
@Configuration
public class ConfiguracionTrazabilidad {

    private static final Logger LOG = LoggerFactory.getLogger(ConfiguracionTrazabilidad.class);

    /** Cada ejecución de un @Scheduled es el origen de su propio correlationId. */
    @Bean
    TaskDecorator correlacionPorEjecucion() {
        return tarea ->
                () -> {
                    try (MDC.MDCCloseable ambito =
                            Correlacion.abrir(Correlacion.normalizar(null))) {
                        tarea.run();
                    }
                };
    }

    /** Cada mensaje de un @RabbitListener se procesa con el correlationId de su cabecera AMQP. */
    @Bean
    static BeanPostProcessor correlacionEnListeners() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String nombre) {
                if (bean instanceof AbstractRabbitListenerContainerFactory<?> fabrica) {
                    fabrica.setAdviceChain(conCorrelacion(fabrica.getAdviceChain()));
                }
                return bean;
            }
        };
    }

    /** Va primero en la cadena: los reintentos del mismo mensaje conservan su correlationId. */
    private static Advice[] conCorrelacion(Advice[] actuales) {
        MethodInterceptor correlacion = ConfiguracionTrazabilidad::procesarMensaje;
        return actuales == null
                ? new Advice[] {correlacion}
                : Stream.concat(Stream.of(correlacion), Stream.of(actuales)).toArray(Advice[]::new);
    }

    static Object procesarMensaje(MethodInvocation invocacion) throws Throwable {
        Object[] argumentos = invocacion.getArguments();
        if (argumentos.length < 2 || !(argumentos[1] instanceof Message mensaje)) {
            return invocacion.proceed();
        }
        MessageProperties propiedades = mensaje.getMessageProperties();
        String correlationId =
                Correlacion.normalizar(propiedades.getHeader(Correlacion.CABECERA_AMQP));
        try (MDC.MDCCloseable ambito = Correlacion.abrir(correlationId)) {
            LOG.atInfo()
                    .addKeyValue("cola", propiedades.getConsumerQueue())
                    .addKeyValue("tipo", propiedades.getType())
                    .addKeyValue("idMensaje", propiedades.getMessageId())
                    .log("mensaje recibido");
            return invocacion.proceed();
        }
    }
}
