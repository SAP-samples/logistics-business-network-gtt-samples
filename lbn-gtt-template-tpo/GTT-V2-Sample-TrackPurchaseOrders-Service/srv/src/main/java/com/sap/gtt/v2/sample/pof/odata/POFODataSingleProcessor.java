package com.sap.gtt.v2.sample.pof.odata;

import static com.sap.gtt.v2.sample.pof.exception.POFServiceException.MESSAGE_CODE_INTERNAL_SERVER_ERROR;

import com.sap.gtt.v2.sample.pof.exception.POFServiceException;
import com.sap.gtt.v2.sample.pof.odata.callback.ExpandEntityCallback;
import com.sap.gtt.v2.sample.pof.odata.callback.ExpandEntitySetCallback;
import com.sap.gtt.v2.sample.pof.odata.handler.ODataHandlerFactory;
import com.sap.gtt.v2.sample.pof.odata.handler.POFODataHandler;
import com.sap.gtt.v2.sample.pof.odata.helper.ODataResultList;
import org.apache.olingo.odata2.api.ODataCallback;
import org.apache.olingo.odata2.api.batch.BatchHandler;
import org.apache.olingo.odata2.api.batch.BatchRequestPart;
import org.apache.olingo.odata2.api.batch.BatchResponsePart;
import org.apache.olingo.odata2.api.commons.InlineCount;
import org.apache.olingo.odata2.api.edm.EdmException;
import org.apache.olingo.odata2.api.edm.EdmMultiplicity;
import org.apache.olingo.odata2.api.edm.EdmNavigationProperty;
import org.apache.olingo.odata2.api.ep.EntityProvider;
import org.apache.olingo.odata2.api.ep.EntityProviderBatchProperties;
import org.apache.olingo.odata2.api.ep.EntityProviderWriteProperties;
import org.apache.olingo.odata2.api.exception.ODataException;
import org.apache.olingo.odata2.api.processor.ODataResponse;
import org.apache.olingo.odata2.api.processor.ODataSingleProcessor;
import org.apache.olingo.odata2.api.uri.ExpandSelectTreeNode;
import org.apache.olingo.odata2.api.uri.NavigationPropertySegment;
import org.apache.olingo.odata2.api.uri.PathInfo;
import org.apache.olingo.odata2.api.uri.UriParser;
import org.apache.olingo.odata2.api.uri.info.GetEntitySetCountUriInfo;
import org.apache.olingo.odata2.api.uri.info.GetEntitySetUriInfo;
import org.apache.olingo.odata2.api.uri.info.GetEntityUriInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Scope("prototype")
public class POFODataSingleProcessor extends ODataSingleProcessor {

    @Autowired
    private ODataHandlerFactory oDataHandlerFactory;

    @Override
    public ODataResponse readEntitySet(GetEntitySetUriInfo uriInfo, String contentType) throws ODataException {

        POFODataHandler handler = oDataHandlerFactory.getHandler(uriInfo.getTargetType());
        ODataResultList<Map<String, Object>> res = handler.handleReadEntitySet(uriInfo, getContext());

        EntityProviderWriteProperties.ODataEntityProviderPropertiesBuilder propertiesBuilder = EntityProviderWriteProperties
                .serviceRoot(getServiceRoot());

        ExpandSelectTreeNode expandSelectTreeNode = UriParser.createExpandSelectTree(uriInfo.getSelect(), uriInfo.getExpand());
        Map<String, ODataCallback> callbacks = setExpandOptionCallback(uriInfo.getExpand());
        propertiesBuilder.expandSelectTree(expandSelectTreeNode).callbacks(callbacks).inlineCountType(uriInfo.getInlineCount());
        propertiesBuilder.inlineCount(InlineCount.ALLPAGES.equals(uriInfo.getInlineCount()) ? res.getCount() : null);
        return EntityProvider.writeFeed(contentType, uriInfo.getTargetEntitySet(), res.getResults(), propertiesBuilder.build());
    }

    @Override
    public ODataResponse readEntity(GetEntityUriInfo uriInfo, String contentType) throws ODataException {

        POFODataHandler handler = oDataHandlerFactory.getHandler(uriInfo.getTargetType());
        Map<String, Object> res = handler.handleReadEntity(uriInfo, getContext());

        ExpandSelectTreeNode expandSelectTreeNode = UriParser.createExpandSelectTree(uriInfo.getSelect(), uriInfo.getExpand());
        EntityProviderWriteProperties.ODataEntityProviderPropertiesBuilder propertiesBuilder = EntityProviderWriteProperties
                .serviceRoot(this.getServiceRoot());
        Map<String, ODataCallback> callbacks = setExpandOptionCallback(uriInfo.getExpand());
        propertiesBuilder.expandSelectTree(expandSelectTreeNode).callbacks(callbacks);
        EntityProviderWriteProperties properties = propertiesBuilder.build();

        return EntityProvider.writeEntry(contentType, uriInfo.getTargetEntitySet(), res, properties);
    }

    @Override
    public ODataResponse countEntitySet(GetEntitySetCountUriInfo uriInfo, String contentType) throws ODataException {
        POFODataHandler handler = oDataHandlerFactory.getHandler(uriInfo.getTargetType());
        Integer count = handler.handleCountEntitySet(uriInfo, this.getContext());
        return EntityProvider.writeText(count.toString());
    }

    @Override
    public ODataResponse executeBatch(BatchHandler handler, String contentType, InputStream content)
            throws ODataException {

        PathInfo pathInfo = getContext().getPathInfo();
        EntityProviderBatchProperties batchProperties = EntityProviderBatchProperties.init().pathInfo(pathInfo).build();
        List<BatchRequestPart> batchRequestParts = EntityProvider.parseBatchRequest(contentType, content, batchProperties);
        List<BatchResponsePart> batchResponseParts = new ArrayList<>();

        for (BatchRequestPart part : batchRequestParts) {
            batchResponseParts.add(handler.handleBatchPart(part));
        }

        return EntityProvider.writeBatchResponse(batchResponseParts);
    }

    private URI getServiceRoot() throws ODataException {
        return getContext().getPathInfo().getServiceRoot();
    }

    private Map<String, ODataCallback> setExpandOptionCallback(List<ArrayList<NavigationPropertySegment>> expand) throws ODataException {
        Map<String, ODataCallback> callbacks = new HashMap<>();
        ExpandEntityCallback expandEntityCallback = new ExpandEntityCallback();
        ExpandEntitySetCallback expandEntitySetCallback = new ExpandEntitySetCallback();

        expandEntityCallback.setServiceRoot(getServiceRoot());
        expandEntitySetCallback.setServiceRoot(getServiceRoot());

        List<NavigationPropertySegment> navi = expand.stream().flatMap(Collection::stream).collect(Collectors.toList());

        for (NavigationPropertySegment prop : navi) {
            EdmNavigationProperty navigationProperty = prop.getNavigationProperty();
            try {
                if (navigationProperty.getMultiplicity().equals(EdmMultiplicity.MANY)) {
                    callbacks.put(prop.getNavigationProperty().getName(), expandEntitySetCallback);
                } else {
                    callbacks.put(prop.getNavigationProperty().getName(), expandEntityCallback);
                }
            } catch (EdmException e) {
                throw new POFServiceException(e,MESSAGE_CODE_INTERNAL_SERVER_ERROR, HttpStatus.INTERNAL_SERVER_ERROR.value());
            }
        }

        return callbacks;
    }


}
