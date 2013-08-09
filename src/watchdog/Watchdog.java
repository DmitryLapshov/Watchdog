package watchdog;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 *
 * @author kit
 * 
 * Supported commands:
 *  execute
 *  disable
 *  set
 *  user (uses http post with 'application/x-www-form-urlencoded; charset=utf-8')
 *  open (uses http get with 'text/html; charset=utf-8')
 *  post (uses http post with 'application/json; charset=utf-8')
 *  find (uses regex matching)
 *  include
 */
public class Watchdog extends DefaultHandler {
    private static String dateFormatNow;
    private static String logsFolder;
    private static String currentFolder;
    private static String backupsFolder;
    private static String started;
    private static String logFile;
    private static String reportFile;
    private static String paramsFile;
    private static String mailSubject;
    private static String mailServer;
    private static String mailPort;
    private static String mailAuth;
    private static String mailTransport;
    private static String mailUser;
    private static String mailPassword;
    private static String mailFrom;
    private static String mailTo;
    private static String mailCc;
    private static String userAgent;
    private static int executed;
    private static int attempts;
    private static int timeout;
    private static int maxLogSize;
    private static LinkedList<String> paths;
    private static LinkedList<String> responses;
    private static ArrayList<String> files;
    private static Request request;
    private static CustomPattern matching;
    private static PrintStream newPrintStream;
    private static StringBuilder report;
    private static boolean zipLogFolder;
    private static boolean evenodd;
    private static boolean error;
    private static boolean mail;
    private static boolean removeResponses;
    
    private static long folderSize(File directory) {
        long length = 0;
        
        if(directory.exists()) {
            for (File file : directory.listFiles()) {
                if (file.isFile()) {
                    length += file.length();
                }
                else {
                    length += folderSize(file);
                }
            }
        }
        return length;
    }
    
    private static void zipFolder(String srcFolder, String destZipFile) throws Exception {
        ZipOutputStream zip;
        FileOutputStream fileWriter;

        fileWriter = new FileOutputStream(destZipFile);
        zip = new ZipOutputStream(fileWriter);

        addFolderToZip("", srcFolder, zip);
        zip.flush();
        zip.close();
    }

    private static void addFileToZip(String path, String srcFile, ZipOutputStream zip) throws Exception {
        File folder = new File(srcFile);
        
        if (folder.isDirectory()) {
            addFolderToZip(path, srcFile, zip);
        }
        else {
            byte[] buf = new byte[1024];
            int len;
            try (FileInputStream in = new FileInputStream(srcFile)) {
                zip.putNextEntry(new ZipEntry(path + "/" + folder.getName()));
                while ((len = in.read(buf)) > 0) {
                    zip.write(buf, 0, len);
                }
            }
        }
    }

    private static void addFolderToZip(String path, String srcFolder, ZipOutputStream zip) throws Exception {
        File folder = new File(srcFolder);

        for (String fileName : folder.list()) {
            if("".equals(path)) {
                addFileToZip(folder.getName(), srcFolder + "/" + fileName, zip);
            }
            else {
                addFileToZip(path + "/" + folder.getName(), srcFolder + "/" + fileName, zip);
            }
        }
    }
    
    private static void cleanFolder(File file) {
        if(file.exists()) {
            if(file.isDirectory()) {
                for(File f : file.listFiles()) {
                    cleanFolder(f);
                }
            }
            file.delete();
        }
    }

    private static void zipAndClean(File logs) {
        long length = folderSize(logs);

        if(length < maxLogSize) {
            return;
        }
        
        File backups = new File(backupsFolder);
        
        if(!backups.exists()) {
            if(!backups.mkdir()) {
                System.out.println("Unable to create backups folder!!!");
                System.exit(1);
            }
        }
        
        try {
            String destZipFile = backupsFolder + "/" + logsFolder + " backup " + started + ".zip";
            destZipFile = destZipFile.replaceAll("[ :-]", "_");
            zipFolder(logsFolder, destZipFile);
            cleanFolder(logs);
            System.out.println(destZipFile + " created");
        }
        catch (Exception ex) {
            System.out.println(ex.toString());
            System.exit(1);
        }
    }
    
    private class DisabledBySchedule extends SAXException {
        public DisabledBySchedule(String s) {
            super(s);
        }
    }
    
    private static class CustomPattern {
        private String name;
        private boolean found;
        
        public CustomPattern(String name) {
            this.name = name;
        }
        
        public void reportIt() {
            StringBuilder result = new StringBuilder("MATCH \"");
            result.append(name).append(found? "\" FOUND" : "\" NOT FOUND!!! ");
            System.out.println(result);
            report.append("<tr style = \"background-color: ")
                .append(evenodd? "lightgray" : "white");
            if(!found) {
                report.append("; font-weight: bold; color: red");
            }
            report.append(";\"><td style = \"padding: 0.3em; border: black solid 1px;\" colspan = \"3\">")
                .append(encodeHTML(name))
                .append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append(found? "Passed" : "Failed")
                .append("</td></tr>\n");
            evenodd = !evenodd;
        }
        
        public void find() {
            Pattern p = Pattern.compile(name);
            Matcher m = p.matcher(responses.getLast());
            found = m.find();
            if(found) {
                responses.add(m.group());
            }
            else {
                saveLastResponse(".txt");
                responses.add("");
                error = true;
            }
        }
    }
    
    private static class Request {
        public int respCode;
        public long timespan;
        public String name;
        public String method;
        public String respMessage;
        public String respException;

        public Request(String name) {
            this.name = name;
        }
        
        public boolean isRespOK() {
            return (respCode == HttpURLConnection.HTTP_OK || 
                    respCode == HttpURLConnection.HTTP_NO_CONTENT)? true : false;
        }

        public String get(String type) {
            URL url;
            HttpURLConnection con;
            String line;
            InputStream _is;
            method = "GET";
            StringBuilder buff = new StringBuilder();
            long started = System.currentTimeMillis();
            try {
                url = new URL(name);
                con = (HttpURLConnection)url.openConnection();
                con.setConnectTimeout(timeout);
                con.setReadTimeout(timeout);
                con.setRequestMethod(method);
                con.setRequestProperty("Connection", "close");
                con.setRequestProperty("Content-Type", type);
                con.setRequestProperty("Content-Language", "en-US");
                con.addRequestProperty("User-Agent", userAgent);
                //con.setUseCaches(true);
                respCode = con.getResponseCode();
                respMessage = con.getResponseMessage();
                if(respCode < 400) {
                    _is = con.getInputStream();
                }
                else {
                /* error from server */
                    _is = con.getErrorStream();
                }
                try (BufferedReader br = new BufferedReader(new InputStreamReader(_is, "UTF-8"))) {
                    while ((line = br.readLine()) != null){
                        buff.append(line).append("\n");
                    }
                }
                _is.close();
                con.disconnect();
            }
            catch(IOException e) {
                respCode = 0;
                respException = e.toString();
                System.out.println(respException);
            }
            finally {
                timespan = System.currentTimeMillis() - started;
                return buff.toString();
            }
        }

        public String post(String message, String type) {
            URL url;
            HttpURLConnection con;
            String line;
            InputStream _is;
            method = "POST";
            StringBuilder buff = new StringBuilder();
            long started = System.currentTimeMillis();
            try {
                url = new URL(name);
                con = (HttpURLConnection)url.openConnection();
                con.setRequestMethod(method);
                con.setDoOutput(true);
                con.setConnectTimeout(timeout);
                con.setReadTimeout(timeout);
                con.setRequestProperty("Connection", "close");
                con.setRequestProperty("Content-Type", type);
                con.setRequestProperty("Content-Length", Integer.toString(message.getBytes().length));
                con.setRequestProperty("Content-Language", "en-US");
                con.addRequestProperty("User-Agent", userAgent);
                try (DataOutputStream dos = new DataOutputStream(con.getOutputStream())) {
                    dos.writeBytes(message);
                    dos.flush();
                }
                respCode = con.getResponseCode();
                respMessage = con.getResponseMessage();
                if(respCode < 400) {
                    _is = con.getInputStream();
                }
                else {
                /* error from server */
                    _is = con.getErrorStream();
                }
                try (BufferedReader br = new BufferedReader(new InputStreamReader(_is, "UTF-8"))) {
                    while ((line = br.readLine()) != null){
                        buff.append(line).append("\n");
                    }
                }
                _is.close();
                con.disconnect();
            }
            catch(IOException e) {
                respCode = 0;
                respException = e.toString();
                System.out.println(respException);
            }
            finally {
                timespan = System.currentTimeMillis() - started;
                return buff.toString();
            }
        }
        
        public void reportIt() {
            StringBuilder sb = new StringBuilder(method);
            sb.append(" ").append(name).append(" (").append(timespan).append(" ms) ");
            if(respCode == 0) {
                sb.append(respException);
            }
            else {
                sb.append(respCode).append(": ").append(respMessage);
            }
            System.out.println(sb);
            report.append("<tr style = \"background-color: ")
                .append(evenodd? "lightgray" : "white");
            if(!isRespOK()) {
                report.append("; font-weight: bold; color: red");
            }
            report.append(";\"><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append(name).append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append(timespan).append(" ms")
                .append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append((respCode == 0)? respException : Integer.toString(respCode) + ": " + respMessage)
                .append("</td><td style = \"padding: 0.3em; border: black solid 1px;\">")
                .append(isRespOK()? "Passed" : "Failed")
                .append("</td></tr>\n");
            evenodd = !evenodd;
        }
    }
    
    private static String encodeHTML(String s) {
        char c;
        StringBuilder out = new StringBuilder();
        for(int i = 0; i < s.length(); i++) {
            c = s.charAt(i);
            if(c > 127 || c == '"' || c == '<' || c=='>') {
                out.append("&#").append(Integer.toString(c)).append(";");
            }
            else {
                out.append(c);
            }
        }
        return out.toString();
    }
    
    private static void sendReport() {
        Multipart mp;
        MimeBodyPart mbp;
        FileDataSource fds;
        try {
            Properties props = new Properties();
            props.put("mail.smtp.host", mailServer);
            props.put("mail.smtp.port", mailPort);
            props.put("mail.smtp.auth", mailAuth);
            Session session = Session.getDefaultInstance(props, null);
            // Construct the message
            Message msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(mailFrom));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(mailTo));
            msg.addRecipient(Message.RecipientType.CC, new InternetAddress(mailCc));
            msg.setSubject(mailSubject + " (" + now("EEE, d MMM yyyy HH:mm:ss Z") + ")");
            //Message body
            mp = new MimeMultipart();
            // Attachments
            for(String s : files) {
                mbp = new MimeBodyPart();
                fds = new FileDataSource(s);
                mbp.setDataHandler(new DataHandler(fds));
                mbp.setFileName(fds.getName());
                mbp.setHeader("Content-Transfer-Encoding", "base64");
                mbp.setHeader("Content-Type", "text/html; charset=utf-8");
                mp.addBodyPart(mbp);
            }
            // Report
            mbp = new MimeBodyPart();
            mbp.setContent(report.toString(), "text/html; charset=utf-8");
            mbp.setHeader("Content-Transfer-Encoding", "base64");
            mp.addBodyPart(mbp);
            //
            msg.setContent(mp);
            Transport tran = session.getTransport(mailTransport);
            tran.connect(mailServer, mailUser, mailPassword);
            tran.sendMessage(msg, msg.getAllRecipients());
            tran.close();
            System.out.println("EMAIL SENT");
        }
        catch(Exception e) {
            System.out.println(e.toString());
        }
    }
    
    private static void saveLastResponse(String ext) {
        File logs, curDir;
        StringBuilder p = new StringBuilder();
        try {            
            logs = new File(logsFolder);
            if(!logs.exists()){
                if(!logs.mkdir()){
                    System.out.println("Unable to create Log folder!");
                    return;
                }
            }
            p.append(logsFolder).append("/").append(started.replaceAll("[/:\\ ]+", "_"));
            currentFolder = p.toString();
            curDir = new File(currentFolder);
            if(!curDir.exists()){
                if(!curDir.mkdir()) {
                    System.out.println("Unable to create Log's sub-folder!");
                    return;
                }
            }
            p.append("/").append(request.name.replaceAll("[/\\: ]+", "_")).append(ext);
            try (BufferedWriter out = new BufferedWriter(new FileWriter(p.toString()))) {
                out.write(responses.getLast());
                out.flush();
            }
            files.add(p.toString());
            System.out.print(p);
            System.out.println(" SAVED");
        }
        catch (Exception e) {
            System.out.println(e.toString());
        }
    }
    
    private static void removeResponses() {
        File file;
        for(String f : files) {
            System.out.println("Deleting " + f);
            file = new File(f);
            file.delete();
        }
        if(currentFolder != null) {
            System.out.println("Deleting " + currentFolder);
            file = new File(currentFolder);
            file.delete();
        }
    }
    
    private static void loadConstants(Attributes attrs) {
        dateFormatNow = attrs.getValue("DATE_FORMAT_NOW");
        logsFolder = attrs.getValue("logsfolder");
        backupsFolder = attrs.getValue("backupsfolder");
        zipLogFolder = Boolean.parseBoolean(attrs.getValue("ziplogfolder"));
        removeResponses = Boolean.parseBoolean(attrs.getValue("removeresponses"));
        maxLogSize = Integer.parseInt(attrs.getValue("maxlogsize"));
        attempts = Integer.parseInt(attrs.getValue("attempts"));
        timeout = Integer.parseInt(attrs.getValue("timeout"));
        userAgent = attrs.getValue("useragent");
        mail = Boolean.parseBoolean(attrs.getValue("mail"));
        mailSubject = attrs.getValue("mailsubject");
        mailServer = attrs.getValue("mailserver");
        mailPort = attrs.getValue("mailport");
        mailAuth = attrs.getValue("mailauth");
        mailTransport = attrs.getValue("mailtransport");
        mailUser = attrs.getValue("mailuser");
        mailPassword = attrs.getValue("mailpassword");
        mailFrom = attrs.getValue("mailfrom");
        mailTo = attrs.getValue("mailto");
        mailCc = attrs.getValue("mailcc");
    }
    
    private static String now() {
        return now(dateFormatNow);
    }
    
    private static String now(String dateFormat) {
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat(dateFormat);
        return sdf.format(cal.getTime());
    }
    
    private static void prepareExecution() {
        paths = new LinkedList<>();
        paths.add("");
        responses = new LinkedList<>();
        files = new ArrayList<>();
        report = new StringBuilder();
        report.append("<!DOCTYPE html>\n<html>\n<head>\n")
            .append("<title>Monitoring started: ").append(started).append("</title>\n")
            .append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\" />\n</head>\n<body>\n")
            .append("<div>\n<table style = \"font-family: arial; font-style: normal; ")
            .append("font-size: 0.7em; width: 100%; padding: 0.5em; ")
            .append("border: black solid 1px; border-collapse: collapse;\">\n")
            .append("<tr style = \"background-color: lightgray;\">")
            .append("<th style = \"padding: 0.3em; width: 60%; border: black solid 1px;\">Operation")
            .append("</th><th style = \"padding: 0.3em; width: 8%; border: black solid 1px;\">Timing")
            .append("</th><th style = \"padding: 0.3em; width: 21%; border: black solid 1px;\">Response")
            .append("</th><th style = \"padding: 0.3em; width: 11%; border: black solid 1px;\">Result")
            .append("</th></tr>\n");
        
       
        if(zipLogFolder) {
            zipAndClean(new File(logsFolder));
        }        
        
        if(logFile != null) {
            File logs = new File(logsFolder);
            if(!logs.exists()) {
                if(!logs.mkdir()) {
                    System.out.println("Unable to create logs folder!!!");
                    System.exit(1);
                }
            }
            File f = new File(logsFolder + "/" + logFile);
            try {
                newPrintStream = new PrintStream(new FileOutputStream(f, true));
                System.setOut(newPrintStream);
            }
            catch(Exception ex) {
                System.out.println(ex.toString());
                System.exit(1);
            }
        }
    }
    
    private static void logIn(String user, String password) {
        if(request.isRespOK()) {
            Pattern p = Pattern.compile("__VIEWSTATE.+?value=\"(.+?)\"");
            Matcher m = p.matcher(responses.getLast());
            String message = String.format(
                "__VIEWSTATE=%1$s&Login1$UserName=%2$s&Login1$Password=%3$s&Login1$LoginButton=Sign+In",
                m.find(1)? m.group(1): "", 
                user, 
                password);
            doPost(message, "application/x-www-form-urlencoded; charset=utf-8");                
        }
    }
    
    private static void doOpen() {
        doGet("text/html; charset=utf-8");
    }
    
    private static void doGet(String type) {
        String response = "";
        for(int i = 0; i < attempts ; i++) {
            request = new Request(paths.getLast());
            response = request.get(type);
            if(request.respCode != 0) {
                break;
            }
            pause();
        }
        responses.add(response);
        if(!request.isRespOK()) {
            error = true;
            if(request.respCode != 0) {
                saveLastResponse(".txt");
            }
        }
    }
    
    private static void doPost(String message) {
        doPost(message, "application/json; charset=utf-8");
    }
    
    private static void doPost(String message, String type) {
        String response = "";
        for(int i = 0; i < attempts ; i++) {
            request = new Request(paths.getLast());
            response = request.post(message, type);
            if(request.respCode != 0) {
                break;
            }
            pause();
        }
        responses.add(response);
        if(!request.isRespOK()){
            error = true;
            if(request.respCode != 0) {
                saveLastResponse(".txt");
            }
        }
    }
    
    private static void doMatch(String pt) {
        matching = new CustomPattern(pt);
        matching.find();
    }
    
    private static void saveReport() {
        File logs;
        StringBuilder p = new StringBuilder();
        try {            
            logs = new File(logsFolder);
            if(!logs.exists()){
                if(!logs.mkdir()){
                    System.out.println("Unable to create Log folder!");
                    return;
                }
            }
            p.append(logsFolder).append("/").append(reportFile);
            try (BufferedWriter out = new BufferedWriter(new FileWriter(p.toString()))) {
                out.write(report.toString());
                out.flush();
            }
            System.out.print(p);
            System.out.println(" SAVED");
        }
        catch(Exception e) {
            System.out.println(e.toString());
        }
    }
    
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
        switch (qName) {
            case "constants":
                if(executed == 0) {
                    loadConstants(attrs);
                }
                break;
            case "execute":
                if(executed == 0) {
                    started = now();
                    prepareExecution();
                    System.out.println("Started: " + started);
                }
                executed++;
                break;
            case "disable":
                String from = attrs.getValue("from");
                String till = attrs.getValue("till");
                String current = now("HH:mm");
                if(0 <= current.compareTo(from) && current.compareTo(till) <= 0) {
                    throw new DisabledBySchedule("Aborted by Schedule from " + from + " till " + till);
                }
                break;
            case "set":
                paths.add(paths.getLast() + attrs.getValue("name"));
                break;
            case "open":
                paths.add(paths.getLast() + attrs.getValue("name"));
                doOpen();
                request.reportIt();
                break;
            case "post":
                paths.add(paths.getLast() + attrs.getValue("name"));
                doPost(attrs.getValue("message"));
                request.reportIt();
                break;
            case "user":
                logIn(attrs.getValue("name"), attrs.getValue("password"));
                request.reportIt();
                break;
            case "find":
                if(request.isRespOK()) {
                    doMatch(attrs.getValue("name"));
                    matching.reportIt();
                }
                break;
            case "include":
                parseXML(attrs.getValue("name"));
                break;
        }
    }
    
    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        switch (qName) {
            case "set":
                paths.removeLast();
                break;
            case "open":
                responses.removeLast();
                paths.removeLast();
                break;
            case "post":
                responses.removeLast();
                paths.removeLast();
                break;
            case "user":
                if(request.isRespOK()) {
                    responses.removeLast();
                }
                break;
            case "find":
                if(request.isRespOK()) {
                    responses.removeLast();
                }
                break;
            case "execute":
                if(executed == 1) {
                    report.append("</table>\n</div>\n</body>\n</html>\n");
                    if(mail && error) {
                        sendReport();
                    }
                    if(reportFile != null) {
                        saveReport();
                    }
                    if(removeResponses) {
                        removeResponses();
                    }
                    System.out.println((error? "Finished with error(s): " : "Finished: ") + now());
                }
                executed--;
                break;
        }
    }
    
    private static void pause() {
        try {
            Thread.sleep(timeout);
        }
        catch (InterruptedException ex) {
            System.out.println(ex.toString());
            System.exit(1);
        }
    }
    
    private static void parseArgs(String[] args) {
        boolean valid = true;
        int i = 0;
        while(i < args.length - 1) {
            switch (args[i]) {
                case "-log":
                    logFile = args[i + 1];
                    break;
                case "-out":
                    reportFile = args[i + 1];
                    break;
                case "-params":
                    paramsFile = args[i + 1];
                    break;
                default:
                    valid = false;
                    break;
            }
            if(!valid){
                break;
            }
            i+=2;
        }
        if(!valid || paramsFile == null) {
            System.out.println("java -jar Watchdog.jar -params parameters.xml [-log log.txt] [-out report.htm]");
            System.exit(1);            
        }
    }
    
    private static void parseXML(String file){
        try{
            File xmlFile = new File(file);
            InputSource is = new InputSource(new InputStreamReader(new FileInputStream(xmlFile), "UTF-8"));
            is.setEncoding("UTF-8");
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            DefaultHandler handler = new Watchdog();
            saxParser.parse(is, handler);
        }
        catch(DisabledBySchedule ex){
            System.out.println(ex.getMessage());
        }
        catch(Exception ex){
            System.out.println(ex.toString());
        }
    }
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        parseArgs(args);
        
        CookieManager cm = new CookieManager();
        cm.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        CookieHandler.setDefault(cm);
        
        parseXML(paramsFile);
        
        System.out.println();
    }
}
