/**
 * This program runs as a server and controls the force to be applied to balance the Inverted Pendulum system running on the clients.
 */
import java.io.*;
import java.net.*;
import java.util.*;

public class ControlServer {

    private static ServerSocket serverSocket;
    private static final int port = 25533;

    /**
     * Main method that creates new socket and PoleServer instance and runs it.
     */
    public static void main(String[] args) throws IOException {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException ioe) {
            System.out.println("unable to set up port");
            System.exit(1);
        }
        System.out.println("Waiting for connection");
        do {
            Socket client = serverSocket.accept();
            System.out.println("\nnew client accepted.\n");
            PoleServer_handler handler = new PoleServer_handler(client);
        } while (true);
    }
}

/**
 * This class sends control messages to balance the pendulum on client side.
 */
class PoleServer_handler implements Runnable {
    // Set the number of poles
    private static final int NUM_POLES = 2;
    private static final double TRACK_LIMIT = 4.8;


    static ServerSocket providerSocket;
    Socket connection = null;
    ObjectOutputStream out;
    ObjectInputStream in;
    String message = "abc";
    static Socket clientSocket;
    Thread t;

    /**
     * Class Constructor
     */
    public PoleServer_handler(Socket socket) {
        t = new Thread(this);
        clientSocket = socket;

        try {
            out = new ObjectOutputStream(clientSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(clientSocket.getInputStream());
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        t.start();
    }
    double angle, angleDot, pos, posDot, action = 0, i = 0;

    /**
     * This method receives the pole positions and calculates the updated value
     * and sends them across to the client.
     * It also sends the amount of force to be applied to balance the pendulum.
     * @throws ioException
     */
    void control_pendulum(ObjectOutputStream out, ObjectInputStream in) {
        try {
            while(true){
                System.out.println("-----------------");

                // read data from client
                Object obj = in.readObject();

                // Do not process string data unless it is "bye", in which case,
                // we close the server
                if(obj instanceof String){
                    System.out.println("STRING RECEIVED: "+(String) obj);
                    if(obj.equals("bye")){
                        break;
                    }
                    continue;
                }

                double[] data= (double[])(obj);
                assert(data.length == NUM_POLES * 4);
                double[] actions = new double[NUM_POLES];

                // Get sensor data of each pole and calculate the action to be
                // applied to each inverted pendulum
                // TODO: Current implementation assumes that each pole is
                // controlled independently. This part needs to be changed if
                // the control of one pendulum needs sensing data from other
                // pendulums.
                for (int i = 0; i < NUM_POLES; i++) {
                  angle = data[i*4+0];
                  angleDot = data[i*4+1];
                  pos = data[i*4+2];
                  posDot = data[i*4+3];

                  double dest = 3;
                  if (i == 0)
                    dest = follow(i, data, true);

                  System.out.println("server < pole["+i+"]: "+angle+"  "
                      +angleDot+"  "+pos+"  "+posDot);
                  actions[i] = calculate_action(angle, angleDot, pos, posDot, dest);
                }

                sendMessage_doubleArray(actions);

            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        try {
            if (clientSocket != null) {
                System.out.println("closing down connection ...");
                out.writeObject("bye");
                out.flush();
                in.close();
                out.close();
                clientSocket.close();
            }
        } catch (IOException ioe) {
            System.out.println("unable to disconnect");
        }

        System.out.println("Session closed. Waiting for new connection...");

    }

    double follow(int thisPole, double [] data, boolean goinRight) {

      double cartWidth = 0.4;
      double breakBuffer = 0.25 + cartWidth;
      double pos = data[thisPole*4+2];
      // default not to hit a wall
      double minDelta = goinRight ? 10 - breakBuffer : -10 + breakBuffer;
      //save cart i's position
      for (int i = 0; i < NUM_POLES; i++){
        // need two nummbers that are closest to less than
        // or greater than pos-
        double otherPos = data[i*4+2];
        double delta = otherPos - pos;
        int targetPole = -1;

        if (goinRight && (delta > 0)) {
          if (delta < minDelta) {
            targetPole = i;
            minDelta = delta;
          }
        } else if (!goinRight && (delta < 0)) {
          if (delta > minDelta) {
            targetPole = i;
            minDelta = delta;
          }
        }
      }
      
      return pos + minDelta + (goinRight ? -1 * breakBuffer : breakBuffer); 

    }

    /**
     * This method calls the controller method to balance the pendulum.
     * @throws ioException
     */
    public void run() {

        try {
            control_pendulum(out, in);

        } catch (Exception ioException) {
            ioException.printStackTrace();
        } finally {
        }

    }

    // Calculate the actions to be applied to the inverted pendulum from the
    // sensing data.
    // TODO: Current implementation assumes that each pole is controlled
    // independently. The interface needs to be changed if the control of one
    // pendulum needs sensing data from other pendulums.
    double calculate_action(double angle, double angleDot, double pos, double posDot, double dest) {
      double action = 0;

      
      double move = (pos - dest) > 0 ?
          // min / max add slow start near boundaries
          Math.min((pos - dest)  / 2, (TRACK_LIMIT - pos) * 3)
          : Math.max((pos - dest)  / 2, (TRACK_LIMIT + pos) * -3);

      action = 10 / (80 * 0.01745) * angle + angleDot + posDot + move;
      //  if (angle > 0) {
      //      if (angle > 65 * 0.01745) {
      //          action = 10;
      //      } else if (angle > 60 * 0.01745) {
      //          action = 8;
      //      } else if (angle > 50 * 0.01745) {
      //          action = 7.5;
      //      } else if (angle > 30 * 0.01745) {
      //          action = 4;
      //      } else if (angle > 20 * 0.01745) {
      //          action = 2;
      //      } else if (angle > 10 * 0.01745) {
      //          action = 0.5;
      //      } else if(angle >5*0.01745){
      //          action = 0.2;
      //      } else if(angle >2*0.01745){
      //          action = 0.1;
      //      } else {
      //          action = 0;
      //      }
      //  } else if (angle < 0) {
      //      if (angle < -65 * 0.01745) {
      //          action = -10;
      //      } else if (angle < -60 * 0.01745) {
      //          action = -8;
      //      } else if (angle < -50 * 0.01745) {
      //          action = -7.5;
      //      } else if (angle < -30 * 0.01745) {
      //          action = -4;
      //      } else if (angle < -20 * 0.01745) {
      //          action = -2;
      //      } else if (angle < -10 * 0.01745) {
      //          action = -0.5;
      //      } else if(angle <-5*0.01745){
      //          action = -0.2;
      //      } else if(angle <-2*0.01745){
      //          action = -0.1;
      //      } else {
      //          action = 0;
      //      }
      //  } else {
      //      action = 0.;
      //  }
      return action;
   }

    /**
     * This method sends the Double message on the object output stream.
     * @throws ioException
     */
    void sendMessage_double(double msg) {
        try {
            out.writeDouble(msg);
            out.flush();
            System.out.println("server>" + msg);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    /**
     * This method sends the Double message on the object output stream.
     */
    void sendMessage_doubleArray(double[] data) {
        try {
            out.writeObject(data);
            out.flush();

            System.out.print("server> ");
            for(int i=0; i< data.length; i++){
                System.out.print(data[i] + "  ");
            }
            System.out.println();

        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }


}
