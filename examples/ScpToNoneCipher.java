/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
import com.jcraft.jsch.*;
import java.awt.*;
import javax.swing.*;
import java.io.*;

/**
 * This program will demonstrate the file transfer from local to remote
 * without using encryption, to speed up transport.
 *
 * You will be asked passwd. 
 * If everything works fine, a local file 'file1' will copied to
 * 'file2' on 'remotehost'.
 *
 * This example implements the source mode of the secure copy protocol
 * (which is about the same as RCP when run over SSH).
 *
 * In addition, it demonstrates switching off the encryption so the
 * uploading will work faster. (It is still secure against modifications
 * of the file (or metadata), just not against reading the contents.)
 *
 * The encryption will only be switched off after authentication, so
 * password sniffing is not possible. (And it will only be switched off
 * if the server supports the "none" cipher, too.)
 *
 * @see <a href="http://blogs.oracle.com/janp/entry/how_the_scp_protocol_works">
 *    How the SCP protocol works</a>
 */
public class ScpToNoneCipher{
  public static void main(String[] arg){
    if(arg.length!=2){
      System.err.println("usage: java ScpTo file1 user@remotehost:file2");
      System.exit(-1);
    }      

    FileInputStream fis=null;
    try{

      String lfile=arg[0];
      String user=arg[1].substring(0, arg[1].indexOf('@'));
      arg[1]=arg[1].substring(arg[1].indexOf('@')+1);
      String host=arg[1].substring(0, arg[1].indexOf(':'));
      String rfile=arg[1].substring(arg[1].indexOf(':')+1);

      JSch jsch=new JSch();
      Session session=jsch.getSession(user, host, 22);

      // username and password will be given via UserInfo interface.
      UserInfo ui=new SwingDialogUserInfo();
      session.setUserInfo(ui);
      session.connect();

      session.setConfig("cipher.s2c", "none,aes128-cbc,3des-cbc,blowfish-cbc");
      session.setConfig("cipher.c2s", "none,aes128-cbc,3des-cbc,blowfish-cbc");

      session.rekey();

      // exec 'scp -t rfile' remotely
      String command="scp -p -t "+rfile;
      Channel channel=session.openChannel("exec");
      ((ChannelExec)channel).setCommand(command);

      // get I/O streams for remote scp
      OutputStream out=channel.getOutputStream();
      InputStream in=channel.getInputStream();

      channel.connect();

      if(checkAck(in)!=0){
        System.exit(1);
      }

      // send "C0644 filesize filename", where filename should not include '/'
      long filesize=(new File(lfile)).length();
      command="C0644 "+filesize+" ";
      if(lfile.lastIndexOf('/')>0){
        command+=lfile.substring(lfile.lastIndexOf('/')+1);
      }
      else{
        command+=lfile;
      }
      command+="\n";
      out.write(command.getBytes()); out.flush();

      if(checkAck(in)!=0){
        System.exit(1);
      }

      // send a content of lfile
      fis=new FileInputStream(lfile);
      byte[] buf=new byte[1024];
      while(true){
        int len=fis.read(buf, 0, buf.length);
        if(len<=0) break;
        out.write(buf, 0, len); out.flush();
      }
      fis.close();
      fis=null;

      // send '\0'
      buf[0]=0; out.write(buf, 0, 1); out.flush();

      if(checkAck(in)!=0){
        System.exit(1);
      }

      session.disconnect();

      System.exit(0);
    }
    catch(Exception e){
      System.out.println(e);
      try{if(fis!=null)fis.close();}catch(Exception ee){}
      System.exit(1);
    }
  }

  static int checkAck(InputStream in) throws IOException{
    int b=in.read();
    // b may be 0 for success,
    //          1 for error,
    //          2 for fatal error,
    //          -1
    if(b==0) return b;
    if(b==-1) return b;

    if(b==1 || b==2){
      StringBuffer sb=new StringBuffer();
      int c;
      do {
        c=in.read();
        sb.append((char)c);
      }
      while(c!='\n');
      if(b==1){ // error
        System.out.print(sb.toString());
      }
      if(b==2){ // fatal error
        System.out.print(sb.toString());
      }
    }
    return b;
  }

}