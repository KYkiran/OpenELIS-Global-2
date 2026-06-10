package org.openelisglobal.atomfeed.controller;

import org.openelisglobal.atomfeed.dto.OpenElisAccessionDto;
import org.openelisglobal.atomfeed.service.AtomFeedResultFeedService;
import org.openelisglobal.atomfeed.service.OpenElisAccessionJsonBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints consumed by the Bahmni OpenMRS openelis-atomfeed-client. Its
 * OpenElisAuthenticator POSTs loginName/password to the same URL it is about to
 * GET and requires HTTP 200, so both endpoints accept GET and POST.
 */
@RestController
@RequestMapping({ "/ws/feed", "/rest/openmrs-atomfeed" })
public class OpenMrsAtomFeedController {

    @Autowired
    private AtomFeedResultFeedService resultFeedService;

    @Autowired
    private OpenElisAccessionJsonBuilder accessionJsonBuilder;

    @RequestMapping(value = "/result/recent", method = { RequestMethod.GET,
            RequestMethod.POST }, produces = MediaType.APPLICATION_ATOM_XML_VALUE)
    public ResponseEntity<String> getRecentResultFeed() {
        return ResponseEntity.ok(resultFeedService.buildRecentFeedXml());
    }

    @RequestMapping(value = "/accession/{accessionKey}", method = { RequestMethod.GET,
            RequestMethod.POST }, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<OpenElisAccessionDto> getAccessionJson(@PathVariable("accessionKey") String accessionKey) {
        OpenElisAccessionDto dto = accessionJsonBuilder.build(accessionKey);
        if (dto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(dto);
    }
}
