/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.androidextra;

import java.util.ArrayList;

import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.IngestModule;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestMessage;

public class AndroidExtraIngestModule implements DataSourceIngestModule {
    private IngestJobContext context = null;
  
    @Override
      public void startUp(IngestJobContext context) throws IngestModuleException {
        this.context = context;
                }

    @Override
    public ProcessResult process(Content dataSource, DataSourceIngestModuleProgress progressBar) {
       ArrayList<String> errors = new ArrayList<>();
       
        
        FileManager fileManager = Case.getCurrentCase().getServices().getFileManager();
      
        try {  
            int imageFileNo = GoogleVisionApiAnalyzer.getImageFileList(dataSource, fileManager, context).size();
            progressBar.switchToDeterminate(1+
                imageFileNo);

            FacebookMessageAnalyzer.findFacebookMessages(dataSource, fileManager, context);
            progressBar.progress(1); 
            GoogleVisionApiAnalyzer.findInappropriateContent(dataSource, fileManager, context, progressBar);
            
            if (context.dataSourceIngestIsCancelled()) {
                return IngestModule.ProcessResult.OK;
            }
        } catch (Exception e) {
            errors.add("Error getting Contacts"); //NON-NLS
        }

        return IngestModule.ProcessResult.OK;
    }

  
}
