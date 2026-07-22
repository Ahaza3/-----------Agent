package com.powerload.service;

import com.powerload.mapper.AlertEventMapper;
import com.powerload.mapper.PredictionResultMapper;
import com.powerload.ml.FlaskInferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SystemHealthServiceTest {

    @Test
    void shouldTreatRedisAsDisabledWhenConnectionFactoryIsAbsent() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(2)).thenReturn(true);

        @SuppressWarnings("unchecked")
        ObjectProvider<RedisConnectionFactory> redisProvider = mock(ObjectProvider.class);
        when(redisProvider.getIfAvailable()).thenReturn(null);

        FlaskInferenceService flaskService = mock(FlaskInferenceService.class);
        when(flaskService.getHealth()).thenReturn(Map.of("healthy", false, "model_type", "UNKNOWN"));

        SystemHealthService service = new SystemHealthService(
                dataSource,
                redisProvider,
                flaskService,
                mock(PredictionResultMapper.class),
                mock(AlertEventMapper.class));
        ReflectionTestUtils.setField(service, "redisHost", "localhost");
        ReflectionTestUtils.setField(service, "llmApiKey", "");
        ReflectionTestUtils.setField(service, "llmModel", "deepseek-chat");

        Map<String, Object> health = service.check();

        assertEquals("UP", health.get("mysql"));
        assertEquals("DISABLED", health.get("redis"));
    }
}
