package com.company.Project1A;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.net.Socket;
import java.io.*;
import java.util.Scanner;

import static com.company.Project1A.HelperClass.computeXORChecksum;
import static com.company.Project1A.HelperClass.parseMessage;

/**
       For submission when ready
   1.  send IAM 30285758  to date.cs.umass.edu, 20001 to get response
   2.  get the file from test server
   3,  finally, on the same opened TCP connection, write 5 byte array to  date.cs.umass.edu, 18765
-----------------------------------------------
       Step 1
       Socket gradingSock = connect(gradingServerIP, gradingServerPort);
       gradingSock.write("IAM studentID\n"); // parse challengeFilename from response

       Step 2
       byte[] body = ClientServer.fastP2PDownload(file, gradingServerIP, torrentServerPort);
                   or ClientServer.slowClientServerDownload(file, gradingServerIP, clientServerPort);

       Step 3
       byte[] checksum = computeXORChecksum(body);
       gradingSock.write(checksum); // four bytes
       gradingSock.write('\n'); // one byte

*/
public class ClientServer1A {
    public static String output;

    public static void main(String[] args) throws Exception {
        //String    ip = args[0];
        //Integer port = Integer.parseInt(args[1]);


        // PRODUCTION Slow Servers:                        date.cs.umass.edu 18765
        // TESTING Quick Server Download RedSox Picture:   plum.cs.umass.edu 18765
        String ip = "date.cs.umass.edu";
        Integer port = 18765;

        Socket challengeSocket = new Socket(ip, port);
        Socket gradingSocket = new Socket("date.cs.umass.edu", 20001);

        DataOutputStream outToGradingServer = new DataOutputStream(gradingSocket.getOutputStream());
        DataInputStream inFromGradingServer = new DataInputStream(gradingSocket.getInputStream());

        DataOutputStream outToChallengeServer = new DataOutputStream(challengeSocket.getOutputStream());
        DataInputStream inFromChallengeServer = new DataInputStream(challengeSocket.getInputStream());

        // This buffer will contain the entire server response,
        // THEN we will create a new Image array for the jpg image itself
        byte[] buf1 = new byte[1000000];  // 1 million bytes, 1MB, 1000KB

            try {
                while(true) {
                    System.out.println("In try block");

                    // Keep this GRADING server connection open so you respond to it with your XOR answer later
                    String query = "IAM 30285758\n";
                    byte[] queryBuf = query.getBytes();
                    outToGradingServer.write(queryBuf);

                    // FOR PRODUCTION SERVER
                    outToChallengeServer.writeBytes("GET 30285758_redsox.jpg\n");
                    System.out.println("---> SENT to challenge server: GET 30285758_redsox.jpg");

                    // RECEIVE Grading Server 1st Response
                    byte[] grading1stResponse = new byte[100000];
                    inFromGradingServer.read(grading1stResponse);
                    System.out.println(new String(grading1stResponse, "UTF-8"));

                    // FOR TESTING SERVER
                    //outToChallengeServer.writeBytes("GET Redsox.jpg\n");


                    // Ex of byte int values: 20,30,10,10
                    int numBytesHeader = 0;
                    byte currByte;
                    byte prevByte = -1;

                    while((currByte = inFromChallengeServer.readByte()) != -1) { // about 61 bytes. [0->60], 61
                        if(currByte == 10 && prevByte == 10) {
                            buf1[numBytesHeader++] = currByte;
                            break;
                        }
                        prevByte = currByte;
                        buf1[numBytesHeader++] = currByte;
                    }
                    System.out.println(currByte + "," + prevByte);
                    System.out.println("numBytesHeader including new line char:" + numBytesHeader); // Now on empty index e.g. 61 for message after the \n char of header


                   // String messageStr = new String(buf1, "UTF-8");
                    //   System.out.println(messageStr);
                   // break;

                    int byte_offset = parseMessage(buf1)[0];
                    int byte_length = parseMessage(buf1)[1];
                    System.out.println("byteoffset:" + byte_offset);
                    System.out.println("Byte length: " + byte_length);

                    // Read the given number of bytes from status message
                    // Account for long lived connection and delay with while loop?
                    byte[] serverImage = new byte[byte_length];


                    // inFromTestingServer inputstream ALREADY consumed the header 61 bytes
                    // NOW need to consume the message. DON'T NEED TO SKIP ANY BYTES BECAUSE ALREADY CONSUMED
                    inFromChallengeServer.readFully(serverImage, byte_offset, byte_length);

                   /* while((numBytesRead = inFromTestingServer.read(serverImage, byte_offset, byte_length)) != -1) {
                        totalRead += numBytesRead;
                        //System.out.println(totalRead);
                        if(totalRead >= byte_length) break;
                    }
                    testingSocket.close();
                    System.out.println("FINISHED reading input stream. \nTOTAL BYTES READ:" + totalRead);*/

                    byte[] checksum = computeXORChecksum(serverImage);
                    outToGradingServer.write(checksum);
                    System.out.println("---> Sent checksum to Grading Server");

                    // RECEIVE Grading Server 2nd Response
                    byte[] grading2ndResponse = new byte[100000];
                    inFromGradingServer.read(grading2ndResponse);
                    System.out.println(new String(grading2ndResponse, "UTF-8"));
                    System.out.println("---> Received Grading Server 2nd grade Response");

                    int emptyBytes = 0;
                    for(int i = 0; i < serverImage.length; i++) {
                        if(serverImage[i] == 0)
                            emptyBytes++;
                    }
                    System.out.println("NUMBER EMPTY BYTES:" + emptyBytes);

                    // FOR TESTING SERVER DONWLOADING REDSOX PICTURE, NOT FOR PRODUCTION SERVER
                    /*String path = System.getProperty("user.home") + File.separator + "Downloads" + File.separator + "redsox.jpg";
                    System.out.println(path);
                    InputStream bis = new ByteArrayInputStream(serverImage);
                    if(bis == null) System.out.println("---> Null byte input stream");
                    BufferedImage image = ImageIO.read(bis); // ERROR: reading the bis returns null to image
                    if (image == null) System.out.println("---> Null buffered image. Something wrong with server image byte array data");
                    ImageIO.write(image, "jpg", new File(path));*/
                    System.out.println("Finished Program");
                    break;
                } // while true
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                gradingSocket.close();
                challengeSocket.close();
            }
    }

}
