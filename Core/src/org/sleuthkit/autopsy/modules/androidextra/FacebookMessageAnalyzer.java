/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.androidextra;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.coreutils.MessageNotifyUtil;
import org.sleuthkit.autopsy.datamodel.ContentUtils;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.BlackboardAttribute.ATTRIBUTE_TYPE;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.json.JSONObject;
import org.openide.util.NbBundle;

public class FacebookMessageAnalyzer {
    private static final String moduleName = AndroidExtraModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(FacebookMessageAnalyzer.class.getName());
    private static Blackboard blackboard;
    
    private static final IngestServices services = IngestServices.getInstance();
    
    /*
     * The names of tables that potentially hold Facebook Message in the dbs
    */
    
    private static final Iterable<String> tablesName = Arrays.asList("threads_db2", "");
    public static void findFacebookMessages(Content dataSource, FileManager fileManager,
            IngestJobContext context)
    {
        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        try
        {
            List<AbstractFile> absFiles = fileManager.findFiles(dataSource, "threads_db2");
            for(AbstractFile abstractFile : absFiles)
            {
                try
                {
                    File file = new File(Case.getCurrentCase().getTempDirectory(), abstractFile.getName());
                    ContentUtils.writeToFile(abstractFile, file, context::dataSourceIngestIsCancelled);
                    findFacebookMessageDb(file.toString(), abstractFile);
                }
                catch(IOException e)
                {
                    logger.log(Level.SEVERE, "Error writing temporary call log db to disk",e);
                }
            }
        }
        catch(TskCoreException e)
        {
            logger.log(Level.SEVERE, "Error finding Facebook messages", e);
        }
    }
    
    
    public static void findFacebookMessageDb(String databasePath, AbstractFile absFile)
    {
        if (databasePath == null || databasePath.isEmpty())
            return;
        Connection connection = null;
        Statement stm = null;
        ResultSet resultSet = null;
        Statement stm1 = null;  
        try
        {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
            stm = connection.createStatement();
            stm1 = connection.createStatement();
        }
        catch(ClassNotFoundException | SQLException e)
        {
            logger.log(Level.SEVERE, "Error opening database", e);
            return;
        }
        Collection<BlackboardArtifact> bbartifacts = new ArrayList<>();
        try {
            resultSet  = stm.executeQuery("SELECT * from messages where text not null and sender not null");
//            resultSet1 = statement1.executeQuery("SELECT thread_key FROM messages");
             
            while (resultSet.next()) {
                
                String threadKey = resultSet.getString("thread_key");
                String[] array = threadKey.split(":");
                
                if(array[0].equals("ONE_TO_ONE"))
                {
                    
                    String sender = resultSet.getString("sender");
                   
                    JSONObject o = new JSONObject(sender);
                    String userKey = o.getString("user_key");
                    String senderName = o.getString("name");
                    Long date = resultSet.getLong("timestamp_ms")/1000;
                    String to  = getOneToOneReceiverName(threadKey, userKey, stm1);
                    String text = resultSet.getString("text");
                    BlackboardArtifact bba = absFile.newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_MESSAGE);
                    //Add attributes to artifact, including: direction, from, to, text, datetime, message type
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_DIRECTION, moduleName,
                    direction(threadKey, userKey)));
                    bba.addAttribute(new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_FROM, moduleName,
                    senderName));
                    bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_DATETIME, moduleName, date));
                    bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_PHONE_NUMBER_TO, moduleName,  to));
                    bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_TEXT, moduleName, text));
                    bba.addAttribute(new BlackboardAttribute(ATTRIBUTE_TYPE.TSK_MESSAGE_TYPE, moduleName, 
                    NbBundle.getMessage(FacebookMessageAnalyzer.class, "FacebookMessageAnalyzer.bbAtttribute.facebookMessage")));
//                    bba.addAttribute(new BlackboardAtribute(ATTRIBUTE_TYPE.))
                    bbartifacts.add(bba); 
                    try
                    {
                        blackboard.indexArtifact(bba);
                    }
                    catch(Blackboard.BlackboardException ex)
                    {
                         logger.log(Level.SEVERE, "Unable to index blackboard artifact " + bba.getArtifactID(), ex); //NON-NLS
                        MessageNotifyUtil.Notify.error(
                                NbBundle.getMessage(FacebookMessageAnalyzer.class, "FacebookMessageAnalyzer.indexError.message"), bba.getDisplayName());

                    }
                }
                else if(array[0].equals("GROUP"))
                {

                }
              
            }
            

        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error parsing text message to Blackboard ", e);
        }finally{
            try {
                if(resultSet != null){
                    resultSet.close();
                }
            
                stm.close();
                connection.close();
            } catch (Exception e) {
                logger.log(Level.SEVERE,"Cannot close database connection");
            }
        }
       
    }
    public static String direction(String threadKey, String userKey)
    {
        String[] sender = threadKey.split(":");
        if(userKey.compareTo("FACEBOOK:"+sender[1]) == 0)
             return NbBundle.getMessage(FacebookMessageAnalyzer.class,
                                    "FacebookMessageAnalyzer.bbAttribute.incoming");
        return NbBundle.getMessage(FacebookMessageAnalyzer.class,
                                    "FacebookMessageAnalyzer.bbAttribute.outcoming");
    }
    public static String getOneToOneReceiverName(String threadKey, String senderId, Statement stm)
    {
        String[] threadKeyToken = threadKey.split(":");
        int i = 1;
        
        if(senderId.compareTo("FACEBOOK:"+threadKeyToken[1]) == 0)
            i=2;
        else
            i=1;
        try
        {
            ResultSet rs = stm.executeQuery("SELECT name FROM thread_users WHERE user_key='FACEBOOK:"+threadKeyToken[i]+"'");
            if(rs.next())
                return rs.getString("name");
            return NbBundle.getMessage(FacebookMessageAnalyzer.class,
                    "FacebookMessageAnalyzer.Name.Error");
        }
        catch(SQLException e)
        {
            logger.log(Level.WARNING, "The result set does not have name column");
        }
        return null;      
    }
}
