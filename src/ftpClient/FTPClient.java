package ftpClient;

import com.sun.org.apache.xpath.internal.SourceTree;
import sun.net.ftp.FtpClient;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class FTPClient {
    private final static String host = "127.0.0.1";
    //    private final static String host = "localhost";
//        private final static String host = "172.28.3.19";
    private final static String dir = "F:\\Client";
    private static BufferedReader reader;
    private static PrintWriter writer;
    private static Socket dataSocket;

    public static void main(String[] args) {
        String command;
        String response;
        String fileName;//远端文件名称
        File localFile;//本地文件

        int length;
        long fileSize;
        byte[] byteBuffer = null;

        try (Socket client = new Socket(host, 21);
             BufferedReader iReader = new BufferedReader(new InputStreamReader(client.getInputStream()));
             PrintWriter iWriter = new PrintWriter(new OutputStreamWriter(client.getOutputStream()));
             Scanner scanner = new Scanner(System.in);
        ) {
            reader = iReader;
            writer = iWriter;
            System.out.println(reader.readLine());
            while (true) {//用户身份验证以及数据连接建立部分
                writer.println(scanner.nextLine());
                writer.flush();
                System.out.println(response = reader.readLine());
                if (response.startsWith("230"))
                    break;
            }
            while (true) {//数据传输部分
                System.out.println("\nRequire for command!");
                System.out.print(">");

                command = scanner.nextLine();

                if (command.toUpperCase().startsWith("CWD")) {
                    writer.println(command);
                    writer.flush();
                    System.out.println(response = reader.readLine());
                } else if (command.length() > 3) {
                    switch (command.substring(0, 4).toUpperCase()) {
                        case "LIST": {
                            pasv();
                            writer.println(command);
                            writer.flush();
                            response = reader.readLine();
                            System.out.println(response);
                            BufferedReader tempReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
                            String fileMenu;
                            while ((fileMenu = tempReader.readLine()) != null) {
                                System.out.println(new String(fileMenu.getBytes("ISO-8859-1"), "utf-8"));
                            }
                            tempReader.close();
                            dataSocket.close();
                            System.out.println(reader.readLine());
                            break;
                        }

                        case "STOR": {
                            fileName = command.substring(4).trim();
                            localFile = new File(dir + "/" + fileName);
                            if (!localFile.exists()) {

                                break;
                            }

                            if (localFile.isDirectory()) {
                                writer.println("MKD " + fileName);
                                writer.flush();
                                System.out.println(response = reader.readLine());

                                File[] files = localFile.listFiles();
                                transferDirectory(fileName + "/", files);
                            } else {
                                updateFile("", localFile);

                            }
                            break;
                        }

                        case "RETR": {
                            fileName = command.substring(4).trim();
                            localFile = new File(dir + "/" + fileName);
                            writer.println("SIZE " + fileName);
                            writer.flush();

                            response = reader.readLine();
                            if (response.startsWith("550 ")) {
                                localFile.mkdir();

                                writer.println("CWD " + fileName);
                                writer.flush();
                                if ((response = reader.readLine()).startsWith("250")) {
                                    System.out.println(response);
                                    transferCloudDirectory(fileName + "/");
                                } else {
                                    System.out.println(response);
                                    break;
                                }
                            } else {
                                download(fileName, localFile);

                                break;
                            }
                        }

                        case "QUIT": {
                            writer.println(command);
                            writer.flush();
                            response = reader.readLine();
                            System.out.println(response);
                            return;
                        }

                        case "HELP": {
                            writer.println(command);
                            writer.flush();
                            response = reader.readLine();
                            while (response != null) {
                                System.out.println(response);
                                response = reader.readLine();
                            }
                            System.out.println(111);
                            break;
                        }
                        default: {
                            System.out.println("500 Syntax error, command unrecognized. This may include errors such as command line too long.");
                        }
                    }
                } else {
                    System.out.println("500 Syntax error, command unrecognized. This may include errors such as command line too long..");
                    continue;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void transferDirectory(String path, File[] files) throws IOException {
        String response;
        int length;
        long fileSize;
        byte[] byteBuffer = null;

        for (int i = 0; i < files.length; i++) {
            if (files[i].isDirectory()) {
                writer.println("MKD " + path + files[i].getName());
                writer.flush();
                System.out.println(response = reader.readLine());
                File[] subDirectory = files[i].listFiles();
                transferDirectory(path + files[i].getName() + "/", subDirectory);
            } else
                updateFile(path, files[i]);
        }
        return;
    }

    public static void updateFile(String path, File file) throws IOException {
        String response;
        int length;
        long fileSize;
        byte[] byteBuffer = new byte[1024];

        pasv();
        RandomAccessFile outFile = new RandomAccessFile(file, "r");
        OutputStream outSocket = dataSocket.getOutputStream();
        writer.println("SIZE " + path + "/" + file.getName());
        writer.flush();
        response = reader.readLine();
        System.out.println("size:" + response);
        if (response.contains("550"))
            fileSize = 0;
        else
            fileSize = Long.parseLong(response.substring(3).trim());
        System.out.println("STOR " + path + file.getName());
        writer.println("STOR " + path + file.getName());
        writer.flush();
        System.out.println(response = reader.readLine());
        if (fileSize != 0) {
            outFile.seek(fileSize);
            System.out.println("Resumes upload");
        }
        while ((length = outFile.read(byteBuffer)) != -1) {
            outSocket.write(byteBuffer, 0, length);
        }
        outSocket.close();
        outFile.close();
        dataSocket.close();
        System.out.println(response = reader.readLine());
    }


    public static void pasv() throws IOException {
        String[] dataHost;
        int dataPort;
        String response;
        writer.println("PASV");
        writer.flush();
        System.out.println(response = reader.readLine());
        dataHost = response.split(",|\\(|\\)");
        dataPort = Integer.parseInt(dataHost[5]) * 256 + Integer.parseInt(dataHost[6]);
        dataSocket = new Socket(dataHost[1] + "." + dataHost[2] + "." + dataHost[3] + "." + dataHost[4], dataPort);
    }

    public static String transferCloudDirectory(String path) throws IOException {
        String response;
        String[] menu;
        File localFile;

        pasv();
        writer.println("LIST");
        writer.flush();
        response = reader.readLine();
        System.out.println(response);

        BufferedReader tempReader = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
        String str;
        List<String> fileMenu = new ArrayList();
        while ((str = tempReader.readLine()) != null) {
            fileMenu.add(new String(str.getBytes("ISO-8859-1"), "utf-8"));
        }
        tempReader.close();
        dataSocket.close();
        System.out.println(reader.readLine());
        menu = fileMenu.toArray(new String[fileMenu.size()]);

        for (int i = 0; i < menu.length; i++) {
            menu[i] = menu[i].split("\t")[2];
            localFile = new File(dir + "/" + path + "/" + menu[i]);
            writer.println("size " + menu[i]);
            writer.flush();
            System.out.println(response = reader.readLine());
            if (response.startsWith("550 ")) {
                localFile.mkdir();
                writer.println("CWD " + menu[i]);
                writer.flush();
                System.out.println(response = reader.readLine());
                if (response.startsWith("250 "))
                    path = transferCloudDirectory(path + "/" + menu[i]);
                else {
                    System.out.println(response);
                    break;
                }
            } else {
                download(menu[i], localFile);
            }
        }
        writer.println("CWD ..");
        writer.flush();
        System.out.println(response = reader.readLine());
        return path + "/../";
    }

    public static void download(String fileName, File localFile) throws IOException {
        long fileSize;
        String command;
        String response;
        byte[] byteBuffer;

        fileSize = localFile.length();
        if (fileSize != 0) {
            writer.println("REST " + fileSize);
            writer.flush();
            System.out.println("Resumes download");
        }
        pasv();
        RandomAccessFile inFile = new RandomAccessFile(localFile, "rw");
        InputStream inSocket = dataSocket.getInputStream();
        writer.println("RETR " + fileName);//
        writer.flush();
        System.out.println(response = reader.readLine());
        if (response.toUpperCase().startsWith("550")) {
            inSocket.close();
            inFile.close();
            dataSocket.close();
            return;
        }
        inFile.seek(fileSize);
        byteBuffer = new byte[1024];
        int length;
        while ((length = inSocket.read(byteBuffer)) != -1) {
            inFile.write(byteBuffer, 0, length);
        }
        inSocket.close();
        inFile.close();
        System.out.println(response = reader.readLine());
        dataSocket.close();
    }
}
