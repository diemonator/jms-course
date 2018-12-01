package jms.student.javafx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import model.Student;
import org.json.JSONObject;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Properties;
import java.util.ResourceBundle;

public class StudentController extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(Paths.get("jms-requestor/out/production/resources/guiRegister.fxml").toUri().toURL());
        RegisterController replierController = new RegisterController(primaryStage);
        loader.setController(replierController);
        primaryStage.setTitle("Student registration");
        primaryStage.setScene(new Scene(loader.load()));
        primaryStage.show();
    }

    private class RegisterController implements Initializable {
        @FXML
        private Button btnCreate;
        @FXML
        private TextField tfName;
        @FXML
        private TextField tfPass;

        private Stage stage;

        private RegisterController(Stage stage) {
            this.stage = stage;
        }

        @Override
        public void initialize(URL location, ResourceBundle resources) {
            btnCreate.setOnAction(this::handleButtonAction);
        }

        private void handleButtonAction(ActionEvent event) {
            if (tfName.getText() != null && tfPass.getText() != null) {
                try {
                    stage.close();
                    FXMLLoader loader = new FXMLLoader(Paths.get("jms-requestor/out/production/resources/gui.fxml").toUri().toURL());
                    Student student = new Student(tfName.getText(),tfPass.getText());
                    Controller replierController = new Controller(student);
                    loader.setController(replierController);
                    stage.setTitle("Student application");
                    stage.setScene(new Scene(loader.load()));
                    stage.show();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class Controller implements Initializable {

        private final String MY_REQUEST_QUEUE = "myRequestQueue";

        private Student student;
        private Session session; // session for creating messages, producers and
        private MessageProducer producer; // for sending messages
        private Queue tmpQueue;
        private ArrayList<MapMessage> messages;

        @FXML
        private Button btn;
        @FXML
        private TextField tf;
        @FXML
        private ListView<String> lv;
        @FXML
        private Label lb;

        private Controller(Student student) {
            this.student = student;
        }

        @Override
        public void initialize(URL location, ResourceBundle resources) {
            btn.setOnAction(this::handleButtonAction);
            messages = new ArrayList<>();
            setRequestQueue();
            lb.setText("Hello, "+student.getUserName() + " you will your posted questions and answers right below");
        }

        private void handleButtonAction(ActionEvent event) {
            String text = tf.getText();
            if (text != null)
                sendMessage(text);
        }

        private void setRequestQueue() {
            try {
                Properties props = new Properties();
                props.setProperty(Context.INITIAL_CONTEXT_FACTORY,
                        "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
                props.setProperty(Context.PROVIDER_URL, "tcp://localhost:61616");
                props.put(("queue."+MY_REQUEST_QUEUE), MY_REQUEST_QUEUE);
                Context jndiContext = new InitialContext(props);
                ConnectionFactory connectionFactory = (ConnectionFactory) jndiContext
                        .lookup("ConnectionFactory");
                Connection connection = connectionFactory.createConnection();
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                Destination sendDestination = (Destination) jndiContext.lookup(MY_REQUEST_QUEUE);
                producer = session.createProducer(sendDestination);
                tmpQueue = session.createTemporaryQueue();
                MessageConsumer consumer = session.createConsumer(tmpQueue);
                consumer.setMessageListener(reply -> {
                    try {
                        MapMessage request = findMapMessage(reply.getJMSCorrelationID());
                        if (request != null) {
                            ObservableList<String> listItems = lv.getItems();
                            final int index = listItems.indexOf(request.getString("message"));
                            final String textItem = listItems.get(index) + " --> " + ((TextMessage)reply).getText();
                            Platform.runLater(() -> {
                                lv.getItems().remove(index);
                                lv.getItems().add(index,textItem);
                            });
                        }
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                });
                connection.start();
            } catch (NamingException | JMSException e) {
                e.printStackTrace();
            }
        }

        private MapMessage findMapMessage(String correlationId) throws JMSException {
            for (MapMessage msg: messages) {
                if (msg.getJMSMessageID().equals(correlationId)) {
                    return msg;
                }
            }
            return null;
        }

        private void sendMessage(String text) {
            try {
                MapMessage map = session.createMapMessage();
                JSONObject json = new JSONObject(student);
                map.setString("student",json.toString());
                map.setString("message",text);
                map.setJMSReplyTo(tmpQueue);
                producer.send(map);
                messages.add(map);
                lv.getItems().add(text);
            } catch (JMSException e) {
                e.printStackTrace();
            }
        }
    }
}