/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.sleuthkit.autopsy.modules.androidextra;

import org.openide.util.lookup.ServiceProvider;
import org.openide.util.NbBundle;
import org.sleuthkit.autopsy.coreutils.Version;
import org.sleuthkit.autopsy.ingest.IngestModuleFactory;
import org.sleuthkit.autopsy.ingest.DataSourceIngestModule;
import org.sleuthkit.autopsy.ingest.IngestModuleFactoryAdapter;
import org.sleuthkit.autopsy.ingest.IngestModuleIngestJobSettings;

/**
 *
 * @author Tài Đinh
 */
@ServiceProvider(service = IngestModuleFactory.class) //  
public class AndroidExtraModuleFactory extends IngestModuleFactoryAdapter{
    
    static String getModuleName() {
        return NbBundle.getMessage(AndroidExtraModuleFactory.class, "AndroidModuleFactory.moduleName");
    }

    @Override
    public String getModuleDisplayName() {
        return getModuleName();
    }

    @Override
    public String getModuleDescription() {
        return NbBundle.getMessage(AndroidExtraModuleFactory.class, "AndroidModuleFactory.moduleDescription");
    }

    @Override
    public String getModuleVersionNumber() {
        return Version.getVersion();
    }

    @Override
    public boolean isDataSourceIngestModuleFactory() {
        return true;
    }

    @Override
    public DataSourceIngestModule createDataSourceIngestModule(IngestModuleIngestJobSettings settings) {
        return new AndroidExtraIngestModule();
    }

}