package br.com.orleon.mq.probe.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties(prefix = "mq")
public class MqProperties {

    private String host;
    private int port;
    private String queueManager;
    private String channel;
    private boolean useTls;
    private String cipherSuite;
    private String username;
    private String password;

    @PostConstruct
    void validate() {
        Assert.hasText(host, "MQ host is required");
        Assert.hasText(queueManager, "MQ queueManager is required");
        Assert.hasText(channel, "MQ channel is required");
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getQueueManager() {
        return queueManager;
    }

    public void setQueueManager(String queueManager) {
        this.queueManager = queueManager;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    public boolean isUseTls() {
        return useTls;
    }

    public void setUseTls(boolean useTls) {
        this.useTls = useTls;
    }

    public String getCipherSuite() {
        return cipherSuite;
    }

    public void setCipherSuite(String cipherSuite) {
        this.cipherSuite = cipherSuite;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
