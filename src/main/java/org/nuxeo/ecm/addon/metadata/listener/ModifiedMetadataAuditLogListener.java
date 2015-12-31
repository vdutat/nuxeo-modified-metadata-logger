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

package org.nuxeo.ecm.addon.metadata.listener;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.addon.metadata.ModifiedMetadataLoggerConstants;
import org.nuxeo.ecm.automation.AutomationService;
import org.nuxeo.ecm.automation.InvalidChainException;
import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.OperationException;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DataModel;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.event.CoreEventConstants;
import org.nuxeo.ecm.core.api.event.DocumentEventTypes;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.diff.model.DocumentDiff;
import org.nuxeo.ecm.diff.model.SchemaDiff;
import org.nuxeo.ecm.diff.service.DocumentDiffService;
import org.nuxeo.runtime.api.Framework;


/**
 * 
 */
public class ModifiedMetadataAuditLogListener implements EventListener {

    private static final Log LOGGER = LogFactory.getLog(ModifiedMetadataAuditLogListener.class);
    
    // TODO extract to configurable list in a service
    private static final List<String> metatdataNames = new ArrayList<String>(
            Arrays.asList(
                    "dublincore:title",
                    "file:content"
                    ));
    private static final List<String> dirtyMetatdataNames = new ArrayList<String>(
            Arrays.asList(
                    "dc:title",
                    "dc:description",
                    "content"
                    ));

    public void handleEvent(Event event) throws ClientException {
        DocumentEventContext context = null;
        if (event.getContext() instanceof DocumentEventContext) {
            context = (DocumentEventContext) event.getContext();
        } else {
            return;
        }
        String eventId = event.getName();
        DocumentModel document = context.getSourceDocument();
        if (DocumentEventTypes.BEFORE_DOC_UPDATE.equals(eventId)) {
            Map<String, SchemaDiff> map = getModifiedFieldNames(document, getBeforeUpdateDocument(context));
            if (map != null) {
                for (Entry<String, SchemaDiff> entry : map.entrySet()) {
                    String schema = entry.getKey();
                    for (String field : entry.getValue().getFieldNames()) {
                        if (isConfigured(schema, field)) {
                            String xpath = String.format(getMetadataFormatter(), schema + ":" + field);
                            LOGGER.debug("modified field: " + xpath);
                            addLogEntry(context.getCoreSession(), document, xpath);
                            }
                    }
                }
            }
        }
    }

    private void processDirtyFields(DocumentEventContext context, DocumentModel document) throws ClientException {
        List<String> dirtyFields = new ArrayList<String>();
        //Map<String, Map<String, Object>> dirtyFieldsValue = new HashMap<String, Map<String, Object>>();
        if (document.isDirty()) {
            for (Entry<String, DataModel> entry : document.getDataModels().entrySet()) {
                dirtyFields.addAll(entry.getValue().getDirtyFields());
            }
            for (String field : dirtyFields) {
                if (isDirtyConfigured(field)) {
//                    Map<String, Object> values = new HashMap<String, Object>();
//                    values.put("new", document.getPropertyValue(field));
                    addLogEntry(context.getCoreSession(), document, String.format(getDirtyMetadataFormatter(), field, document.getPropertyValue(field)));
                }
            }
        }
    }

    private void addLogEntry(CoreSession session,
            DocumentModel document, String xpath) {
        OperationContext operationContext = new OperationContext(session);
        operationContext.setInput(document);
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("event", ModifiedMetadataLoggerConstants.EVENT_NAME); 
        params.put("category", ModifiedMetadataLoggerConstants.EVENT_DOCUMENT_METADATA_CATEGORY); 
        params.put("comment", xpath); 
        try {
            Framework.getService(AutomationService.class).run(operationContext, "Audit.Log", params);
        } catch (InvalidChainException e) {
            LOGGER.error("Audit.Log failed.", e);
        } catch (OperationException e) {
            LOGGER.error("Audit.Log failed.", e);
        } catch (Exception e) {
            LOGGER.error("Audit.Log failed.", e);
        }
    }    
    
    public Map<String, SchemaDiff> getModifiedFieldNames(DocumentModel doc, DocumentModel previousDoc) throws ClientException {
        if (previousDoc != null) {
            DocumentDiffService diffService = getDiffService();
            if (diffService != null) {
                DocumentDiff diff = diffService.diff(doc.getCoreSession(), doc, previousDoc);
                return diff.getDocDiff();
            } else {
                LOGGER.error("Unable to get Diff Service, modified metedata won't be logged in document history.");
            }
        } else {
            LOGGER.warn("No 'previous Document Model' data in context");
        }
        return null;
    }
    
    protected DocumentModel getBeforeUpdateDocument(DocumentEventContext context) throws ClientException {
        Map<String, Serializable> properties = context.getProperties();
        for (Entry<String,Serializable> entry : properties.entrySet()) {
            LOGGER.warn("Event context data:" + entry.getKey());
        }
        return (DocumentModel) context.getProperty(CoreEventConstants.PREVIOUS_DOCUMENT_MODEL);
    }

    protected String getMetadataFormatter() {
        return "%s";
    }

    protected String getDirtyMetadataFormatter() {
        return "%s: old value: '%s'";
    }
    
    private DocumentDiffService getDiffService() {
        return Framework.getLocalService(DocumentDiffService.class);
    }
    
    private boolean isConfigured(String schema, String field) {
        // TODO call custom service to get configurable list of metadata to log
        return metatdataNames.contains(schema + ":" + field);
    }
    
    private boolean isDirtyConfigured(String field) {
        // TODO call custom service to get configurable list of metadata to log
        return dirtyMetatdataNames.contains(field);
    }

}
