/*
 * (C) Copyright ${year} Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     vdutat
 */

package org.nuxeo.ecm.test;

import static org.junit.Assert.*;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Predicate;
import org.apache.commons.collections.Transformer;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.addon.metadata.ModifiedMetadataLoggerConstants;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.diff.service.DocumentDiffService;
import org.nuxeo.ecm.platform.audit.AuditFeature;
import org.nuxeo.ecm.platform.audit.api.AuditReader;
import org.nuxeo.ecm.platform.audit.api.DocumentHistoryReader;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.runtime.api.Framework;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.transaction.TransactionHelper;

import com.google.inject.Inject;

/**
 * 
 */

@RunWith(FeaturesRunner.class)
@Features({
    AuditFeature.class
    })
@Deploy({
    "nuxeo-modified-metadata-logger",
    "org.nuxeo.ecm.platform.query.api", "org.nuxeo.ecm.automation.core", "org.nuxeo.ecm.automation.features",
    "org.nuxeo.diff.core",
    "org.nuxeo.ecm.core.io",
	})
public class TestMetadataAuditLogger {

    @Inject
    CoreSession session;
    
    @Inject DocumentDiffService documentDiffService;
    
    @Inject DocumentHistoryReader reader;
    
    @Test public void isDiffServiceDeployed() throws Exception {
        assertNotNull(documentDiffService);
    }
    
    @Test public void isListenerDeployed() throws Exception {
        assertNotNull(Framework.getRuntime().getComponent("org.nuxeo.ecm.addon.metadata.adapter.listener.contrib.ModifiedMetadataAuditLogListener"));
    }
    
    @Test public void isReaderDeployed() throws Exception {
        assertNotNull(reader);
    }
    
    //@Ignore("For some reason 'documentMetadataModified' event cannot be retrieved form document history") 
    @Test public void  logEntryCreated() throws Exception {
        DocumentModel doc = session.createDocumentModel("File");
        assertNotNull(doc);
        doc.setPathInfo("/", "doc1");
        doc = session.createDocument(doc);
        assertNotNull(doc);
        session.save();

        // Wait for the events to be logged.
        TransactionHelper.commitOrRollbackTransaction();
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();

        List<LogEntry> entries = reader.getDocumentHistory(doc, 0, 50);
        for (LogEntry entry : entries) {
            System.out.println("***" + entry.getEventId() + "/" + entry.getComment());
        }
        Predicate predicate = new Predicate(){

            @Override
            public boolean evaluate(Object input) {
                LogEntry entry = ((LogEntry)input);
                if (ModifiedMetadataLoggerConstants.EVENT_DOCUMENT_METADATA_CATEGORY.equals(entry.getCategory())) {
                    return true;
                } else {
                    return false;
                }
            }};
        CollectionUtils.filter(entries, predicate);
        assertTrue(entries.isEmpty());

        doc.setPropertyValue("dc:title", "title");
        doc = session.saveDocument(doc);
        session.save();

        // Wait for the events to be logged.
        TransactionHelper.commitOrRollbackTransaction();
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();
        
        entries = reader.getDocumentHistory(doc, 0, 50);
        for (LogEntry entry : entries) {
            System.out.println("******" + entry.getEventId() + "/" + entry.getComment());
        }
        CollectionUtils.filter(entries, predicate);
        //FIXME this assert fails
        assertFalse(entries.isEmpty());

    }
    
}
