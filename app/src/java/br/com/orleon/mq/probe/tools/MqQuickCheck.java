package br.com.orleon.mq.probe.tools;

import com.ibm.mq.jms.MQConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;

import javax.jms.JMSContext;
import javax.jms.Queue;

public final class MqQuickCheck {

    private MqQuickCheck() {
    }

    public static void main(String[] args) throws Exception {
        MQConnectionFactory factory = new MQConnectionFactory();
        factory.setHostName(System.getProperty("mq.host"));
        factory.setPort(Integer.parseInt(System.getProperty("mq.port")));
        factory.setQueueManager(System.getProperty("mq.qm"));
        factory.setChannel(System.getProperty("mq.channel"));
        factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);
        try (JMSContext ctx = factory.createContext()) {
            Queue q = ctx.createQueue(System.getProperty("mq.queue"));
            ctx.createProducer().send(q, "Test message");
            System.out.println("✅ MQ connection successful — message sent!");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ MQ connection failed: " + e.getMessage());
        }
    }
}
