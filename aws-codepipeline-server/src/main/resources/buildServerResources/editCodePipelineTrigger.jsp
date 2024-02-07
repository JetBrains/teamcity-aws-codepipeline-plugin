<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="l" tagdir="/WEB-INF/tags/layout" %>

<style type="text/css">
    #editTriggerDialog {
        width: 50em;
    }
</style>

<%@ page import="jetbrains.buildServer.codepipeline.CodePipelineConstants" %>

<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>

<c:set var="action_token_param" value="<%=CodePipelineConstants.ACTION_TOKEN_PARAM%>"/>
<c:set var="action_token_label" value="<%=CodePipelineConstants.ACTION_TOKEN_LABEL%>"/>

<jsp:include page="editAWSCommonParams.jsp"/>

<tr>
    <th><label for="${action_token_param}">${action_token_label}: <l:star/></label></th>
    <td><props:textProperty name="${action_token_param}" className="longField" maxlength="256"/>
        <span class="smallNote">Must be unique and match the corresponding field in the TeamCity Action settings in the AWS CodePipeline, satisfy regular expression pattern: [a-zA-Z0-9_-]+] and have length <= 20.</span>
        <span class="error" id="error_${action_token_param}"></span>
    </td>
</tr>