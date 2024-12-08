package utb.fai;

import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import utb.fai.API.HumiditySensor;
import utb.fai.API.IrrigationSystem;

/**
 * Trida MQTT klienta pro mereni vhlkosti pudy a rizeni zavlazovaciho systemu. 
 * 
 * V teto tride implementuje MQTT klienta
 */
public class SoilMoistureMQTTClient {

    private MqttClient client;
    private HumiditySensor humiditySensor;
    private IrrigationSystem irrigationSystem;
    private Timer irrigationTimer;

    /**
     * Vytvori instacni tridy MQTT klienta pro mereni vhlkosti pudy a rizeni
     * zavlazovaciho systemu
     * 
     * @param sensor     Senzor vlhkosti
     * @param irrigation Zarizeni pro zavlahu pudy
     */
    public SoilMoistureMQTTClient(HumiditySensor sensor, IrrigationSystem irrigation) {
        this.humiditySensor = sensor;
        this.irrigationSystem = irrigation;
    }

    /**
     * Metoda pro spusteni klienta
     */
    public void start() {
        try{
            client = new MqttClient(Config.BROKER, Config.CLIENT_ID);
            client.connect();

            client.subscribe(Config.TOPIC_IN);
            client.setCallback(new MqttCallback() {
                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    String messageString = new String(message.getPayload());

                    switch (messageString) {
                        //GET HUMIDITY
                        case Config.REQUEST_GET_HUMIDITY:

                          float humidity = humiditySensor.readRAWValue();
                          String humidity_response = Config.RESPONSE_HUMIDITY + ";" + humidity;
                          client.publish(Config.TOPIC_OUT, new MqttMessage(humidity_response.getBytes()));

                          break;
                        //GET STATUS
                        case Config.REQUEST_GET_STATUS:
                          String irrigation_status;

                          if(irrigationSystem.isActive()){
                            irrigation_status = "irrigation_on";
                          }
                          else{
                            irrigation_status = "irrigation_off";
                          }

                          String status_response = Config.RESPONSE_STATUS + ";" + irrigation_status;
                          client.publish(Config.TOPIC_OUT, new MqttMessage(status_response.getBytes()));

                          break;
                        //ZAPNOUT+TIMER
                        case Config.REQUEST_START_IRRIGATION:
                          
                          if(!irrigationSystem.isActive()){
                            irrigationSystem.activate();
                            if(irrigationSystem.hasFault()){
                                String errorString = "fault;IRRIGATION_SYSTEM";
                                client.publish(Config.TOPIC_OUT, new MqttMessage(errorString.getBytes()));
                                return;
                            }
                            String start_irrigation_response = "status;irrigation_on";
                            client.publish(Config.TOPIC_OUT, new MqttMessage(start_irrigation_response.getBytes()));
                          }

                            if (irrigationTimer != null) {
                                irrigationTimer.cancel();
                            }
                            irrigationTimer = new Timer();
                            irrigationTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    try {
                                        irrigationSystem.deactivate();
                                        if (irrigationSystem.hasFault()) {
                                            String errorString = "fault;IRRIGATION_SYSTEM";
                                            client.publish(Config.TOPIC_OUT, new MqttMessage(errorString.getBytes()));
                                            return;
                                        }
                                        String start_irrigation_response = "status;irrigation_off";
                                        client.publish(Config.TOPIC_OUT, new MqttMessage(start_irrigation_response.getBytes()));
                                    } catch (MqttException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }, 30000);
                          
                          break;
                        //VYPNOUT + TIMER
                        case Config.REQUEST_STOP_IRRIGATION:
                          irrigationSystem.deactivate();
                          
                          if (irrigationTimer != null) {
                            irrigationTimer.cancel();
                          }

                          if (irrigationSystem.hasFault()) {
                            String errorString = "fault;IRRIGATION_SYSTEM";
                            client.publish(Config.TOPIC_OUT, new MqttMessage(errorString.getBytes()));
                            return;
                          }
                          
                          String stop_irrigation_response = "status;irrigation_off";
                          client.publish(Config.TOPIC_OUT, new MqttMessage(stop_irrigation_response.getBytes()));
                          break;
                      }
                }

                @Override
                public void connectionLost(Throwable cause) {}

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {}

            });

            Timer timer = new Timer();
            timer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run(){
                    try{
                        float humidity = humiditySensor.readRAWValue();
                        if (humiditySensor.hasFault()) {
                            String errorString = "fault;HUMIDITY_SENSOR";
                            client.publish(Config.TOPIC_OUT, new MqttMessage(errorString.getBytes()));
                            return;
                          }
                        String humidityString = Config.RESPONSE_HUMIDITY + ";" + humidity;
                        client.publish(Config.TOPIC_OUT, new MqttMessage(humidityString.getBytes()));
                    } catch (Exception e) {
                        System.err.println("AUTOMATIC TIMER ERROR: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            }, 0, 10000);;

        } catch (Exception e) {
            System.err.println("MQTT CLIENT STARTUP ERROR: "+e.getMessage());
            e.printStackTrace();
        }
    }

}
