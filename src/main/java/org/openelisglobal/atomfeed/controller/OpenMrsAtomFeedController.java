package org.openelisglobal.atomfeed.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openelisglobal.atomfeed.dto.OpenElisAccessionDto;
import org.openelisglobal.atomfeed.service.AtomFeedResultFeedService;
import org.openelisglobal.atomfeed.service.OpenElisAccessionJsonBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({ "/ws/feed", "/rest/openmrs-atomfeed" })
public class OpenMrsAtomFeedController {

    @Autowired
    private AtomFeedResultFeedService resultFeedService;

    @Autowired
    private OpenElisAccessionJsonBuilder accessionJsonBuilder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping(value = "/result/recent", produces = MediaType.APPLICATION_ATOM_XML_VALUE)
    public ResponseEntity<String> getRecentResultFeed() {
        return ResponseEntity.ok(resultFeedService.buildRecentFeedXml());
    }

    @GetMapping(value = "/accession/{accessionKey}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAccessionJson(@PathVariable("accessionKey") String accessionKey) throws Exception {
        OpenElisAccessionDto dto = accessionJsonBuilder.build(accessionKey);
        if (dto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        return ResponseEntity.ok(objectMapper.writeValueAsString(dto));
    }
}
