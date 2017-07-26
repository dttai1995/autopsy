/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.androidextra;

import com.google.cloud.vision.v1.AnnotateImageRequest;
import com.google.cloud.vision.v1.AnnotateImageResponse;
import com.google.cloud.vision.v1.BatchAnnotateImagesResponse;
import com.google.cloud.vision.v1.Feature;
import com.google.cloud.vision.v1.Image;
import com.google.cloud.vision.v1.ImageAnnotatorClient;
import com.google.cloud.vision.v1.SafeSearchAnnotation;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.sleuthkit.autopsy.casemodule.Case;
import org.sleuthkit.autopsy.casemodule.services.Blackboard;
import org.sleuthkit.autopsy.casemodule.services.FileManager;
import org.sleuthkit.autopsy.coreutils.Logger;
import org.sleuthkit.autopsy.ingest.IngestJobContext;
import org.sleuthkit.autopsy.ingest.IngestServices;
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.BlackboardArtifact;
import org.sleuthkit.datamodel.BlackboardAttribute;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.ReadContentInputStream;
import java.util.Collections;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModuleProgress;
import org.sleuthkit.autopsy.ingest.ModuleDataEvent;
/**
 *
 * @author Tài Đinh
 */
public class GoogleVisionApiAnalyzer {

    private static final String moduleName = AndroidExtraModuleFactory.getModuleName();
    private static final Logger logger = Logger.getLogger(GoogleVisionApiAnalyzer.class.getName());
    private static Blackboard blackboard;
    private static final IngestServices services = IngestServices.getInstance();
    private final static List<String> IMAGE_EXTENSIONS = Arrays.asList(".jpg", ".jpeg", ".png", ".psd", ".nef", ".tiff", ".bmp", ".tec");
    private final static int LIKELY_LEVEL = 3;
    private final static int MAXIMUM_FILE_PER_REQUEST = 6;
    private static List<AbstractFile> absFiles = null;
    public static int progressDeterminator = 1; 
    private static DataSourceIngestModuleProgress classProgressBar = null;
    public static List getImageFileList(Content dataSource, FileManager fileManager,
            IngestJobContext context) throws TskCoreException
    {
        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        absFiles = fileManager.findFilesByExtensions(dataSource, IMAGE_EXTENSIONS);
        return absFiles;
        
    }
    public static void findInappropriateContent(Content dataSource, FileManager fileManager,
            IngestJobContext context, DataSourceIngestModuleProgress progressBar) {
        classProgressBar =  progressBar;
        blackboard = Case.getCurrentCase().getServices().getBlackboard();
        
        try {
            List<AbstractFile> absFiles = getImageFileList(dataSource, fileManager, context);
               
            int count = 0 ;
            List<AbstractFile> fileList = new ArrayList<>();
            for (AbstractFile abstractFile : absFiles) {
                count++;
                fileList.add(abstractFile);
                if (fileList.size() == MAXIMUM_FILE_PER_REQUEST) {
                    detectInappropriateContent(fileList);
                    count = 0;
                    fileList.clear();
                }
       
            }
            System.out.println("The number of files is: " + count);
            logger.log(Level.SEVERE, "The number of files is: " + count);
        } catch (TskCoreException e) {
            logger.log(Level.SEVERE, "Error when getting the blackboard");
        }

    }

    public static void detectInappropriateContent(List<AbstractFile> abstractFiles){
        List<InputStream> iStream = createInputStreamListFromAbstractFileList(abstractFiles);
        List<AnnotateImageRequest> requests = new ArrayList<>();
        for (InputStream stream : iStream) {
            try {
                ByteString imgBytes = ByteString.readFrom(stream);
            Image img = Image.newBuilder().setContent(imgBytes).build();
            Feature feat = Feature.newBuilder().setType(Feature.Type.SAFE_SEARCH_DETECTION).build();
            AnnotateImageRequest request
                    = AnnotateImageRequest.newBuilder().addFeatures(feat).setImage(img).build();
            requests.add(request);
            } catch (IOException e) {
                System.out.println("Error when reading the file");
            }
            
        }
        
        try {
             BatchAnnotateImagesResponse response =
        ImageAnnotatorClient.create().batchAnnotateImages(requests);
        List<AnnotateImageResponse> responses = response.getResponsesList();
        
        for (AnnotateImageResponse res : responses) {
            if (res.hasError()) {
                System.out.println(res.toString());
                System.out.println("Error with the response");
            }
            SafeSearchAnnotation annotation = res.getSafeSearchAnnotation();
            System.out.println(annotation.getAdultValue() + " " + annotation.getMedicalValue() + " " + annotation.getSpoofValue());
            if (mayBeInappropriate(
                    annotation.getAdultValue(),
                    annotation.getMedicalValue(),
                    annotation.getSpoofValue(),
                    annotation.getViolenceValue())) {
                try {
                    BlackboardArtifact artifact = abstractFiles.get(responses.indexOf(res)).newArtifact(BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT);
                    
                    BlackboardAttribute setNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_SET_NAME, moduleName, "Suspected Images");
                    BlackboardAttribute ruleNameAttribute = new BlackboardAttribute(BlackboardAttribute.ATTRIBUTE_TYPE.TSK_CATEGORY, moduleName, "Images");
                    artifact.addAttribute(setNameAttribute);
                    artifact.addAttribute(ruleNameAttribute);
                    blackboard.indexArtifact(artifact);
                    IngestServices.getInstance().fireModuleDataEvent(new ModuleDataEvent(moduleName, BlackboardArtifact.ARTIFACT_TYPE.TSK_INTERESTING_FILE_HIT, Collections.singletonList(artifact)));
                } catch (Exception e) {
                    System.out.println("Cannot index the blackboard artifact, checking again!!!");
                }

            }
            progressDeterminator+=1;
            classProgressBar.progress(1 + progressDeterminator);
            System.out.println(progressDeterminator);
            
        }
        } catch (Exception e) {
            System.out.println("Error when sending to the API");
        }
    }

    private static boolean mayBeInappropriate(int adult, int medical, int spoof, int violence) {
        return (adult >= LIKELY_LEVEL || 
                medical >= LIKELY_LEVEL || 
                spoof >= LIKELY_LEVEL || 
                violence >= LIKELY_LEVEL);
    }

    private static List<InputStream> createInputStreamListFromAbstractFileList(List<AbstractFile> fileList) {
            List<InputStream> streamList = new ArrayList<>();
            for(AbstractFile absFile : fileList)
            {
               InputStream iStream = new ReadContentInputStream(absFile);
               streamList.add(iStream);
            }
            return streamList;
    }
}
