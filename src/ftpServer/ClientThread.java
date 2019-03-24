package ftpServer;

import java.io.*;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Random;

public class ClientThread extends Thread {
    private Socket client;
    private Socket dataSocket;
    private String dir;
    private String command = null;
    private long offset = 0;
    private Random random = new Random();
    private BufferedReader reader;
    private PrintWriter writer;

    public ClientThread(Socket client, String dir) {
        this.client = client;
        this.dir = dir;
    }

    @Override
    public void run() {
        try (BufferedReader iReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter iWriter = new PrintWriter(new OutputStreamWriter(client.getOutputStream()));
        ) {
            //用户身份验证部分
            reader = iReader;
            writer = iWriter;
            writer.println("220 chen FTP Service");
            writer.flush();
            checkUser("USER", "chen", "331 PASS required for \"chen\".", "530 Invalid user.", "error:login first");
            checkUser("PASS", "111", "230 Logged on.", "530 PASS error", "530 PASS required.");


            while (true) {//数据传输部分
                command = reader.readLine();
                if (command.toUpperCase().startsWith("CWD")) {
                    String str = command.substring(3).trim();
                    File file = new File(dir+"/"+str);
                    if (file.exists()) {
                        this.dir = dir+"/"+str;
                        writer.println("250 CWD successful.");
                        writer.flush();
                        System.out.println(dir);
                    } else {
                        writer.println("550 CWD failed.");
                        writer.flush();
                    }
                } else if (command.toUpperCase().startsWith("MKD")) {
                    String directory = command.substring(3).trim();
                    File file = new File(dir + "/" + directory);
                    if (file.exists()) {
                        writer.println("550 Directory already exists.");
                        writer.flush();
                    } else {
                        file.mkdir();
                        writer.println("200 Success create directory.");
                        writer.flush();
                    }
                } else {
                    switch (command.substring(0, 4).toUpperCase()) {
                        case "PASV": {
                            connectDataPort();
                            break;
                        }
                        case "LIST": {
                            writer.println("150 Openning data channel for directory list.");
                            writer.flush();
                            PrintWriter dataWriter = new PrintWriter(dataSocket.getOutputStream());
                            File fDir = new File(dir);
                            if (!fDir.isDirectory()) {
                                writer.println("500 No such file or directory.");
                                writer.flush();
                            }
                            File[] files = fDir.listFiles();
                            String modifyTime = "";
                            DecimalFormat df = new DecimalFormat("#.00");
                            String size;
                            for (int i = 0; i < files.length; i++) {
                                modifyTime = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss")
                                        .format(new Date(files[i].lastModified()));
                                if (files[i].isDirectory()) {
                                    dataWriter.println("0(Directory)\t" + modifyTime + "\t" + files[i].getName());
                                } else {
                                    if (files[i].length() < 1024) {
                                      size = df.format((double) files[i].length()) + "B";
                                    } else if (files[i].length() < 1048576) {
                                        size = df.format((double) files[i].length() / 1024) + "KB";
                                    } else if (files[i].length() < 1073741824) {
                                        size = df.format((double) files[i].length() / 1048576) + "MB";
                                    } else {
                                        size = df.format((double) files[i].length() / 1073741824) +"GB";
                                    }

                                    dataWriter.println(size + "\t" + modifyTime + "\t" + files[i].getName());
                                }
                            }
                            dataWriter.flush();
                            writer.println("250 Directory successfully changed.");
                            writer.flush();
                            dataWriter.close();
                            dataSocket.close();
                            break;
                        }

                        case "STOR": {
                            String str = command.substring(4).trim();
                            writer.println("150 Opening data channel for file transfer.");
                            writer.flush();
                            RandomAccessFile inFile = null;
                            InputStream inSocket = null;
                            File fDir = new File(dir + "/" + str);
                            inFile = new RandomAccessFile(fDir, "rw");
                            inFile.seek(fDir.length());
                            inSocket = dataSocket.getInputStream();
                            byte byteBuffer[] = new byte[1024];
                            int length;
                            while ((length = inSocket.read(byteBuffer)) != -1) {
                                inFile.write(byteBuffer, 0, length);
                            }
                            writer.println("226 Upload completion.");
                            writer.flush();
                            inSocket.close();
                            inFile.close();
                            dataSocket.close();
                            break;
                        }

                        case "RETR": {
                            String str = command.substring(4).trim();
                            File cloudFile = new File(dir + "/" + str);
                            if (cloudFile.exists()) {
                                writer.println("150 Opening data channel for file transfer.");
                                writer.flush();
                            } else {
                                writer.println("550 Bad file request");
                            }



                            byte byteBuffer[] = new byte[1024];
                            int length;
                            RandomAccessFile outFile = new RandomAccessFile(cloudFile, "r");
                            OutputStream outSocket = dataSocket.getOutputStream();
                            if (offset != 0) {
                                outFile.seek(offset);
                            }
                            offset = 0;
                            while ((length = outFile.read(byteBuffer)) != -1) {
                                outSocket.write(byteBuffer, 0, length);
                            }
                            writer.println("226 Closing data connection. Requested file action successful (for example, file transfer or file abort).");
                            writer.flush();
                            outSocket.close();
                            outFile.close();
                            break;
                        }

                        case "SIZE": {
                            String str = command.substring(4).trim();
                            File file = new File(dir + "/" + str);
                            if (!file.exists()){
                                writer.println("550 Could not get file size.");
                                writer.flush();
                                break;
                            }
                            if (file.isDirectory()){
                                writer.println("550 This is a directory");
                                writer.flush();
                                break;
                            }
                            writer.println(file.length());
                            writer.flush();
                            break;
                        }

                        case "REST": {
                            String str = command.substring(4).trim();
                            offset = Long.parseLong(str);
                            break;
                        }

                        case "QUIT": {
                            writer.println("221 Goodbye.");
                            writer.flush();
                            return;
                        }
                    }
                }
            }
        } catch (
                IOException e)

        {
            e.printStackTrace();
        }finally {
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    public void checkUser(String type, String rightAnswer, String rightResponse, String wrongResponse, String error) throws IOException {
        while (true) {
            command = reader.readLine();
            if (command.toUpperCase().startsWith(type)) {
                command = command.substring(4).trim();
                if (command.equals(rightAnswer)) {
                    writer.println(rightResponse);
                    writer.flush();
                    return;
                } else {
                    writer.println(wrongResponse);
                    writer.flush();
                }
            } else {
                writer.println(error);
                writer.flush();
            }

        }
    }

    public void connectDataPort() throws IOException {
        if (command.toUpperCase().equals("PASV")) {
            int port_high = 0;
            int port_low = 0;
            ServerSocket serverSocket;
            port_high = 1 + random.nextInt(20);
            port_low = 100 + random.nextInt(1000);
            serverSocket = new ServerSocket(port_high * 256 + port_low);
            writer.println("227 Entering passive mode ("
                    + InetAddress.getLocalHost().getHostAddress().replace(".", ",") + "," + port_high + ","
//                                                    + "127,0,0,1,"+port_high+","
                    + port_low + ")");
            writer.flush();
            dataSocket = serverSocket.accept();
            serverSocket.close();
            return;
        } else if (command.toUpperCase().equals("")) {

        } else {
            writer.println("501 Syntax error in parameters or arguments.");
            writer.flush();
        }
    }

}
