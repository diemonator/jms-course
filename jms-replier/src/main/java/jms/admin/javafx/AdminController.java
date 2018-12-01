package jms.admin.javafx;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import model.Student;
import org.json.JSONObject;

import javax.jms.*;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.*;

public class AdminController extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws IOException {
        FXMLLoader loader = new FXMLLoader(Paths.get("jms-replier/out/production/resources/gui.fxml").toUri().toURL());
        Controller replierController = new Controller();
        loader.setController(replierController);
        primaryStage.setTitle("Administrator application");
        primaryStage.setScene(new Scene(loader.load()));
        primaryStage.show();
    }

    private class Controller implements Initializable {

        private final String MY_REQUEST_QUEUE = "myRequestQueue";

        private Session session; // session for creating messages, producers and// reference to a queue/topic destination
        private MessageProducer producer; // for sending messages
        private HashMap<Student, ArrayList<MapMessage>> map;

        @FXML
        private ListView<String> lv;
        @FXML
        private ListView<String> lvMsg;
        @FXML
        private Button btn;
        @FXML
        private TextField tf;

        @Override
        public void initialize(URL location, ResourceBundle resources) {
            btn.setOnAction(this::handleButtonAction);
            lv.setOnMouseClicked(this::handleNameSelect);
            map = new HashMap<>();
            setMyQueues();
        }

        private void handleNameSelect(MouseEvent mouseEvent) {
            lvMsg.getItems().clear();
            String selectedStudent = lv.getSelectionModel().getSelectedItem();
            final ArrayList<MapMessage> messages = map.get(getStudent(selectedStudent));
            Platform.runLater(() -> {
                try {
                    for (MapMessage request: messages) {
                        lvMsg.getItems().add(request.getString("message"));
                    }
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            });
        }

        private Student getStudent(String userName) {
            for (Student s: map.keySet()) {
                if (s.getUserName().equals(userName))
                    return s;
            }
            return null;
        }

        private MapMessage getMapMessage(List<MapMessage> maps, String selectedItem) throws JMSException {
            for (MapMessage map: maps) {
                if (map.getString("message").equals(selectedItem))
                    return map;
            }
            return null;
        }

        private void setMapMessageWithReply(Student student, MapMessage mapMessage, String answer) throws JMSException {
            Student s = getStudent(student.getUserName());
            ArrayList<MapMessage> temp = map.get(s);
            for (int i = 0; i < temp.size(); i++) {
                if (temp.get(i).hashCode() == mapMessage.hashCode()) {
                    temp.remove(i);
                    MapMessage newMapMessage = session.createMapMessage();
                    JSONObject object = new JSONObject(s);
                    newMapMessage.setString("student", object.toString());
                    newMapMessage.setString("message", answer);
                    temp.add(i,newMapMessage);
                }
            }
            map.replace(s,temp);
        }

        private void handleButtonAction(ActionEvent event) {
            String replyBody = tf.getText();
            SelectionModel<String> selection = lvMsg.getSelectionModel();
            String selectedItem = selection.getSelectedItem();
            int index = selection.getSelectedIndex();
            if (replyBody != null && index != -1) {
                try {
                    Message reply = session.createTextMessage(replyBody);
                    final Student student = getStudent(lv.getSelectionModel().getSelectedItem());
                    final ArrayList<MapMessage> messages = map.get(student);
                    final MapMessage selectedRequestMessage = getMapMessage(messages, selectedItem);
                    if (student != null && selectedRequestMessage != null) {
                        reply.setJMSCorrelationID(selectedRequestMessage.getJMSMessageID());
                        final String content = selectedItem + " --> " + replyBody;
                        lvMsg.getItems().remove(index);
                        lvMsg.getItems().add(index, content);
                        producer = session.createProducer(null);
                        producer.send(selectedRequestMessage.getJMSReplyTo(), reply);
                        setMapMessageWithReply(student,selectedRequestMessage, content);
                    }
                } catch (JMSException e) {
                    e.printStackTrace();
                }
            }
        }

        private void setMyQueues() {
            try {
                Properties props = new Properties();
                props.setProperty(Context.INITIAL_CONTEXT_FACTORY,
                        "org.apache.activemq.jndi.ActiveMQInitialContextFactory");
                props.setProperty(Context.PROVIDER_URL, "tcp://localhost:61616");
                // connect to the Destination called “myFirstChannel”
                // queue or topic: “queue.myRequestQueue” or “topic.myRequestQueue”
                props.put(("queue." + MY_REQUEST_QUEUE), MY_REQUEST_QUEUE);
                Context jndiContext = new InitialContext(props);
                ConnectionFactory connectionFactory = (ConnectionFactory) jndiContext
                        .lookup("ConnectionFactory");
                Connection connection = connectionFactory.createConnection();
                session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
                // connect to the sender destination
                Destination receiveDestination = (Destination) jndiContext.lookup(MY_REQUEST_QUEUE);
                MessageConsumer consumer = session.createConsumer(receiveDestination);
                consumer.setMessageListener(request -> {
                    final MapMessage content = (MapMessage) request;
                    try {
                        JSONObject json = new JSONObject(content.getString("student"));
                        Student student = new Student(json.getString("userName"),json.getString("password"));
                        ArrayList<MapMessage> messages;
                        if (!map.containsKey(student)) {
                            messages = new ArrayList<>();
                            messages.add(content);
                            map.put(student,messages);
                            Platform.runLater(() -> lv.getItems().add(student.getUserName()));
                        }
                        else {
                            messages = map.get(student);
                            messages.add(content);
                            map.replace(student,messages);
                        }
                        Platform.runLater(() -> {
                            try {
                                MultipleSelectionModel<String> lvSelected = lv.getSelectionModel();
                                int selectedIndex = lvSelected.getSelectedIndex();
                                if (selectedIndex != -1 && lvSelected.getSelectedItem().equals(student.getUserName()))
                                    lvMsg.getItems().add(content.getString("message"));
                            } catch (JMSException e) {
                                e.printStackTrace();
                            }
                        });
                    } catch (JMSException e) {
                        e.printStackTrace();
                    }
                });
                connection.start();
            } catch (NamingException | JMSException e) {
                e.printStackTrace();
            }
        }
    }
}