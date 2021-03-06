package rabbit.consumer;

import rabbit.Message;
import rabbit.handler.Handler;
import rabbit.handler.HandlerService;
import rabbit.lyra.internal.util.Assert;
import rabbit.messageConverter.MessageConverter;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Created by allen lei on 2016/2/24.
 * simple consumer,
 *
 */
public class SimpleMessageConsumer extends DefaultConsumer {

    private final Logger logger = LoggerFactory.getLogger("#Simple_Message_Consumer#");

    private MessageConverter messageConverter;

    private HandlerService handlerService;

    private String queue;


    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     */
    public SimpleMessageConsumer(Channel channel, HandlerService handlerService, MessageConverter messageConverter, String queue) {
        super(channel);

        Assert.notNull(handlerService);
        Assert.notNull(messageConverter);
        Assert.notNull(queue);
        this.handlerService = handlerService;
        this.messageConverter = messageConverter;
        this.queue = queue;
    }

    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        final Object message = this.messageConverter.convertToObject(body);
        if (message instanceof Message) {
            final Message messageBody = (Message) message;
            final Handler handler = handlerService.getConsumerHandler(queue);

            if(handler == null){
                logger.error("No handler for this message {},so stop handle it and nack.",messageBody.getMessageId());
                getChannel().basicNack(envelope.getDeliveryTag(),false,false);
                return;
            }
            boolean successful = false;
            try {
                successful = handler.handleMessage(messageBody);
            }catch (Throwable e){
                //catch all exception and nack message, if throw any runtime exception,the channel will be closed.
                logger.error("Handler message occur error,the error is {} ",e);
                if(getChannel() != null){
                    getChannel().basicNack(envelope.getDeliveryTag(),false,false);
                }
            }
            logger.info("Message {} handler result is {}",messageBody.getMessageId(),successful);

            if (successful) {
                getChannel().basicAck(envelope.getDeliveryTag(), false);
            } else {
                getChannel().basicNack(envelope.getDeliveryTag(), false, false);
            }

        } else {
            getChannel().basicNack(envelope.getDeliveryTag(), false, false);
            if (logger.isErrorEnabled()) {
                logger.error("invalid message,the valid message type should be {} but current message type is {}", Message.class.getName(),
                        message != null ? message.getClass().getName() : "null");
            }
        }
    }

}
