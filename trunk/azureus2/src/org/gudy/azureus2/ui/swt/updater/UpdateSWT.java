/*
 * File    : UpdateSWT.java
 * Created : 3 avr. 2004
 * By      : Olivier
 * 
 * Azureus - a Java Bittorrent client
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details ( see the LICENSE file ).
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.gudy.azureus2.ui.swt.updater;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * @author Olivier Chalouhi
 *
 */
public class UpdateSWT {
  
  static String userDir;
  public static void main(String args[]) throws Exception {
    userDir = System.getProperty("user.dir") + System.getProperty("file.separator");
    
    
    UpdateLogger.log("SWT Updater started with parameters : ");
    for(int i = 0 ; i < args.length ; i++) {
      UpdateLogger.log(args[i]);
    }
    UpdateLogger.log("-----------------");
    
    if(args.length < 4)
      return;
    try {
      
      UpdateLogger.log("user.dir="  + userDir);      
      UpdateLogger.log("SWT Updater is waiting 1 sec");

      Thread.sleep(1000);
      
      String platform = args[0];
      
      UpdateLogger.log("SWT Updater has detected platform : " + platform );
      
      if(platform.equals("carbon"))
        updateSWT_carbon(args[1]);
      else {
        updateSWT_generic(args[1]);
      }     
      
      restart(args[2],args[3]);
      
      UpdateLogger.log("SWT Updater has finished");     
      
    } catch(Exception e) {
      UpdateLogger.log("SWT Updater has encountered an exception : " + e);
      e.printStackTrace();
    }
  }
  
  public static void updateSWT_generic(String zipFileName) throws Exception {
    UpdateLogger.log("SWT Updater is doing Generic Update");
    
    UpdateLogger.log("SWT Updater is opening zip file : " + userDir + zipFileName);
    
    ZipFile zipFile = new ZipFile(userDir + zipFileName);
    Enumeration enum = zipFile.entries();
    while(enum.hasMoreElements()) {
      ZipEntry zipEntry = (ZipEntry) enum.nextElement();
      
      UpdateLogger.log("\tSWT Updater is processing : " + zipEntry.getName());
      
      if(zipEntry.getName().equals("swt.jar")) {        
        writeFile(zipFile,zipEntry,userDir);
      }
      if(zipEntry.getName().equals("swt-pi.jar")) {        
        writeFile(zipFile,zipEntry,userDir);
      }
      if(zipEntry.getName().equals("swt-mozilla.jar")) {        
        writeFile(zipFile,zipEntry,userDir);
      }     
      if(zipEntry.getName().startsWith("libswt") && zipEntry.getName().endsWith(".so")) {        
        writeFile(zipFile,zipEntry,userDir);
      }
      if(zipEntry.getName().startsWith("swt-win32-") && zipEntry.getName().endsWith(".dll")) {
        writeFile(zipFile,zipEntry,userDir);
      }
    }    
  }
  
  public static void updateSWT_carbon(String zipFileName) throws Exception{
    UpdateLogger.log("SWT Updater is doing Carbon (OSX) Update");
    
    UpdateLogger.log("SWT Updater is opening zip file : " + userDir + zipFileName);
    
    ZipFile zipFile = new ZipFile(userDir +  zipFileName);
    Enumeration enum = zipFile.entries();
    while(enum.hasMoreElements()) {     
      ZipEntry zipEntry = (ZipEntry) enum.nextElement();
      
      UpdateLogger.log("\tSWT Updater is processing : " + zipEntry.getName());
      
      if(zipEntry.getName().equals("java_swt")) {                
        writeFile(zipFile,zipEntry,userDir + "Azureus.app/Contents/MacOS/");
        File f = openFile("Azureus.app/Contents/MacOS/","java_swt");
        String path = f.getAbsolutePath();
        String chgRights = "chmod 755 " + path;
        Process p = Runtime.getRuntime().exec(chgRights);
        p.waitFor();
      }
      if(zipEntry.getName().equals("swt.jar")) {        
        writeFile(zipFile,zipEntry,userDir + "Azureus.app/Contents/Resources/Java/");
      }
      if(zipEntry.getName().startsWith("libswt-carbon-") && zipEntry.getName().endsWith(".jnilib")) {
        writeFile(zipFile,zipEntry,userDir + "Azureus.app/Contents/Resources/Java/dll/");
      }
    }    
  }
   
  public static void writeFile(ZipFile zipFile,ZipEntry zipEntry,String path) throws Exception {
    String toLog = "";
    if(path != null) {
      UpdateLogger.log("\t\tUnzipping file " + zipEntry.getName() + "to path " + path);
    } else {
      UpdateLogger.log("\t\tUnzipping file " + zipEntry.getName());
    }    
    
    String fileName = zipEntry.getName();
        
    File f = openFile(path,fileName);
    
    
    //If file already exists, rename to .old
    if(f.exists()) {
      UpdateLogger.log("\t\tFile exists, renaming to .old");
      
      String backUpName = fileName + ".old";
      File backup =  openFile(path,backUpName);
      if(backup.exists()) {backup.delete(); Thread.sleep(500); }
      if(!f.renameTo(backup)) {
        UpdateLogger.log("\t\tCouldn't rename file");
        
        throw new IOException("File " + fileName + " cannot be renamed into " + backUpName);
      }
    }
    
    f = openFile(path,fileName);
    FileOutputStream fos = new FileOutputStream(f);
    InputStream is = zipFile.getInputStream(zipEntry);
    byte[] buffer = new byte[4096];
    int read = 0;
    while((read = is.read(buffer)) > 0) {
      fos.write(buffer,0,read);
    }
    fos.close();
  }
  
  public static File openFile(String path,String name) throws IOException {
    String fileName = name;
    
    if(path != null) {
      fileName = path + name;            
    }
    
    UpdateLogger.log("\t\t\tOpening : " + fileName );
    
    return new File(fileName);
  }
  
  public static void restart(String userPath,String libPath) throws IOException{
    String osName = System.getProperty("os.name");
    if(osName.equalsIgnoreCase("Linux")) {
      restartLinux(userPath,libPath);
    } else if(osName.equalsIgnoreCase("Mac OS X")) {
      restartOSX(userPath,libPath);
    } else {
      restartWindows(userPath,libPath);
    }
  }
  
  public static void restartLinux(String userPath,String libPath) throws IOException{
    
  }
  
  public static void restartOSX(String userPath,String libPath) throws IOException{
    
  }
  
  public static void restartWindows(String userPath,String libPath) throws IOException{            
    String exec = userPath + "\\Azureus.exe";
    UpdateLogger.log("Restarting with command line : " + exec);
    Runtime.getRuntime().exec(exec);
  }
  
  
}
