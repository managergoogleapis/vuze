/*
 * UserInterfaceMain.java
 *
 * Created on 9. Oktober 2003, 19:50
 */

package org.gudy.azureus2.ui.common;

import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;

import java.net.Socket;

import java.util.HashMap;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import org.gudy.azureus2.core3.config.COConfigurationManager;
import org.gudy.azureus2.core3.global.*;

import org.gudy.azureus2.ui.console.ConsoleInput;

/**
 *
 * @author  Tobias Minich
 */
public class Main {
  
  public static HashMap UIS = null;
  public static String DEFAULT_UI = "swt";
  
  public static GlobalManager GM = null;
  public static StartServer start = null;
  
  private static CommandLine parseCommands(String[] args, boolean constart) {
    
    if (args==null)
      return null;
    
    CommandLineParser parser = new PosixParser();
    Options options = new Options();
    options.addOption("h", "help", false, "Show this help.");
    options.addOption(OptionBuilder.withLongOpt("exec")
                                   .hasArg()
                                   .withArgName("file")
                                   .withDescription("Execute script file.")
                                   .create('e'));
    options.addOption(OptionBuilder.withLongOpt("command")
                                   .hasArg()
                                   .withArgName("command")
                                   .withDescription("Execute single script command.")
                                   .create('c'));
    options.addOption(OptionBuilder.withLongOpt("ui")
                                   .withDescription("Run <uis>. ',' separated list of user interfaces to run. The first one given will respond to requests without determinable source UI (e.g. further torrents added via command line).\r\nAvailable: swt (default), web, console")
                                   .withArgName("uis")
                                   .hasArg()
                                   .create('u'));
    CommandLine commands = null;
    try {
      commands = parser.parse(options, args, true);
    } catch( ParseException exp ) {
      Logger.getLogger("azureus2").error("Parsing failed.  Reason: " + exp.getMessage(), exp);
      if (constart)
        System.exit(2);
    }
    if (commands.hasOption('h')) {
      if (constart) {
        HelpFormatter hf = new HelpFormatter();
        hf.printHelp("java org.gudy.azureus2.ui.common.Main", options, true);
        System.exit(0);
      }
    }
    return commands;
  }
  
  public static void initRootLogger() {
    if (Logger.getRootLogger().getAppender("ConsoleAppender")==null) {
      Appender app;
      app = new ConsoleAppender(new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN));
      app.setName("ConsoleAppender");
      Logger.getRootLogger().addAppender(app);
    }
  }
  
  public static void main(String[] args) {
    initRootLogger();
    CommandLine commands = parseCommands(args, true);

    System.setProperty( "sun.net.client.defaultConnectTimeout", "120000");
    System.setProperty( "sun.net.client.defaultReadTimeout", "60000" );
    start = new StartServer();
      
    if ((start == null) || (start.getState()==StartServer.STATE_FAULTY)) {
       new StartSocket(args);
    } else {
      COConfigurationManager.checkConfiguration();
      start.start();
      
      processArgs(args, true, commands);
    }
  }
  
  public static void shutdown() {
    if (start!=null)
      start.stopIt();
    if (GM!=null)
      GM.stopAll();
    System.exit(0);
  }
  
  public static void processArgs(String[] args, boolean creategm, CommandLine commands) {
    if (commands==null)
      commands = parseCommands(args, false);
    if ((commands!=null) && (args.length>0)) {
      if (UIS == null)
        UIS = new HashMap();
      if (commands.hasOption('u')) {
        String uinames = commands.getOptionValue('u');
        if (uinames.indexOf(',')==-1) {
          if (!UIS.containsKey(uinames))
            UIS.put(uinames,UserInterfaceFactory.getUI(uinames));
        } else {
          StringTokenizer stok = new StringTokenizer(uinames, ",");
          while (stok.hasMoreTokens()) {
            String uin = stok.nextToken();
            if (!UIS.containsKey(uin))
              UIS.put(uin,UserInterfaceFactory.getUI(uin));
          }
        }
      } else {
        if (UIS.isEmpty())
          UIS.put(DEFAULT_UI, UserInterfaceFactory.getUI(DEFAULT_UI));
      }

      Iterator uis = UIS.values().iterator();
      boolean isFirst = true;
      String [] theRest = commands.getArgs();
      while (uis.hasNext()) {
        IUserInterface ui = (IUserInterface) uis.next();
        ui.init(isFirst, (UIS.size()>1));
        theRest = ui.processArgs(theRest);
        isFirst = false;
      }

      if (creategm)
        GM = GlobalManagerFactory.create();

      uis = UIS.values().iterator();
      while (uis.hasNext())
        ((IUserInterface) uis.next()).startUI();
      
      if (commands.hasOption('e')) {
        try {
          new ConsoleInput(commands.getOptionValue('e'), GM, new FileReader(commands.getOptionValue('e')), System.out, false);
        } catch (java.io.FileNotFoundException e) {
          Logger.getLogger("azureus2").error("Script file not found: "+e.toString());
        }
      }
      
      if (commands.hasOption('c')) {
        String comm = commands.getOptionValue('c');
        comm+="\nlogout\n";
        new ConsoleInput(commands.getOptionValue('c'), GM, new StringReader(comm), System.out, false);
      }
      
      openTorrents(theRest);
    } else {
      Logger.getLogger("azureus2").error("No commands to process");
    }
  }
  
  public static void openTorrents(String[] torrents) {
    if ((Main.UIS!=null) && (!Main.UIS.isEmpty()) && (torrents.length>0)) {
      for(int l=0; l<torrents.length; l++) {
        ((IUserInterface) Main.UIS.values().toArray()[0]).openTorrent(torrents[l]);
      }
    }
  }
  
  public static class StartSocket {
    public StartSocket(String args[]) {
      Socket sck = null;
      PrintWriter pw = null;
      try {      
        sck = new Socket("localhost",6880);
        pw = new PrintWriter(new OutputStreamWriter(sck.getOutputStream()));
        StringBuffer buffer = new StringBuffer("args;");
        for(int i = 0 ; i < args.length ; i++) {
          String arg = args[i].replaceAll("&","&&").replaceAll(";","&;");
          buffer.append(arg);
          buffer.append(';');
        }
        pw.println(buffer.toString());
        pw.flush();
      } catch(Exception e) {
        e.printStackTrace();
      } finally {
        try {
          if (pw != null)
            pw.close();
        } catch (Exception e) {
        }
        try {
          if (sck != null)
            sck.close();
        } catch (Exception e) {
        }
      }
    }
  }
}
