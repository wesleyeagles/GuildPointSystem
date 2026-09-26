package com.guild.app.schedule;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "guild.schedule.test-alert")
public class ScheduleTestAlertProperties {

    /** Evento fictício para validar alerta de 10 minutos (desligar em produção). */
    private boolean enabled = false;

    /**
     * O evento de teste começa em (10 min + leadSeconds) após o boot;
     * o alerta dispara ~leadSeconds depois, quando faltam 10 min.
     */
    private int leadSeconds = 15;
}
