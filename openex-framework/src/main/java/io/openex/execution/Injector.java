package io.openex.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.openex.contract.Contract;
import io.openex.database.model.*;
import io.openex.database.model.InjectExpectation.EXPECTATION_TYPE;
import io.openex.database.repository.DocumentRepository;
import io.openex.database.repository.InjectExpectationRepository;
import io.openex.model.*;
import io.openex.model.expectation.ChallengeExpectation;
import io.openex.model.expectation.MediaExpectation;
import io.openex.service.FileService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;

import javax.annotation.Resource;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static io.openex.database.model.ExecutionTrace.traceError;


public abstract class Injector {

    @Resource
    protected ObjectMapper mapper;
    private FileService fileService;
    private DocumentRepository documentRepository;
    private InjectExpectationRepository injectExpectationRepository;

    @Autowired
    public void setInjectExpectationRepository(InjectExpectationRepository injectExpectationRepository) {
        this.injectExpectationRepository = injectExpectationRepository;
    }

    @Autowired
    public void setDocumentRepository(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Autowired
    public void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    public abstract List<Expectation> process(Execution execution, ExecutableInject injection, Contract contract) throws Exception;

    private InjectExpectation expectationConverter(Audience audience, ExecutableInject executableInject, Expectation expectation) {
        InjectExpectation expectationExecution = new InjectExpectation();
        expectationExecution.setExercise(executableInject.getInject().getExercise());
        expectationExecution.setInject(executableInject.getInject());
        expectationExecution.setAudience(audience);
        expectationExecution.setScore(0);
        switch (expectation.type()) {
            case ARTICLE -> expectationExecution.setArticle(((MediaExpectation)expectation).article());
            case CHALLENGE -> expectationExecution.setChallenge(((ChallengeExpectation)expectation).challenge());
            case DOCUMENT -> expectationExecution.setType(EXPECTATION_TYPE.DOCUMENT);
            case TEXT -> expectationExecution.setType(EXPECTATION_TYPE.TEXT);
            default -> throw new IllegalStateException("Unexpected value: " + expectation);
        }
        return expectationExecution;
    }

    private Execution execute(ExecutableInject executableInject, boolean scheduleInjection) {
        Execution execution = new Execution();
        try {
            // Inject contract must exist
            Contract contract = executableInject.getContract();
            // Inject contract must be exposed
            if (!contract.getConfig().isExpose()) {
                throw new UnsupportedOperationException("Inject is not activated for execution");
            }
            // If empty content, inject must be rejected
            if (executableInject.getInject().getContent() == null) {
                throw new UnsupportedOperationException("Inject is empty");
            }
            // If inject is too old, reject the execution
            if (scheduleInjection && !isInInjectableRange(executableInject.getSource())) {
                throw new UnsupportedOperationException("Inject is now too old for execution");
            }
            // Process the execution
            List<Expectation> expectations = process(execution, executableInject, contract);
            // Create the expectations
            List<Audience> audiences = executableInject.getInject().getAudiences();
            if (scheduleInjection && audiences.size() > 0 && expectations.size() > 0) {
                List<InjectExpectation> executions = audiences.stream()
                        .flatMap(audience -> expectations.stream()
                                .map(expectation -> expectationConverter(audience, executableInject, expectation)))
                        .toList();
                injectExpectationRepository.saveAll(executions);
            }
        } catch (Exception e) {
            execution.addTrace(traceError(getClass().getSimpleName(), e.getMessage(), e));
        } finally {
            execution.stop();
        }
        return execution;
    }

    public Execution executeInRange(ExecutableInject executableInject) {
        return execute(executableInject, true);
    }

    public Execution executeDirectly(ExecutableInject executableInject) {
        return execute(executableInject, false);
    }

    // region utils
    private boolean isInInjectableRange(Injection injection) {
        Instant now = Instant.now();
        Instant start = now.minus(Duration.parse("PT1H"));
        Instant injectWhen = injection.getDate().orElseThrow();
        return injectWhen.isAfter(start) && injectWhen.isBefore(now);
    }

    public <T> T contentConvert(ExecutableInject injection, Class<T> converter) throws Exception {
        Inject inject = injection.getInject();
        ObjectNode content = inject.getContent();
        return mapper.treeToValue(content, converter);
    }

    public List<DataAttachment> resolveAttachments(Execution execution, List<Document> documents) {
        List<DataAttachment> resolved = new ArrayList<>();
        for (Document attachment : documents) {
            String documentId = attachment.getId();
            Optional<Document> askedDocument = documentRepository.findById(documentId);
            try {
                Document doc = askedDocument.orElseThrow();
                InputStream fileInputStream = fileService.getFile(doc).orElseThrow();
                byte[] content = IOUtils.toByteArray(fileInputStream);
                resolved.add(new DataAttachment(doc.getName(), content, doc.getType()));
            } catch (Exception e) {
                // Can't fetch the attachments, ignore
                String docInfo = askedDocument.map(Document::getName).orElse(documentId);
                String message = "Error getting document " + docInfo;
                execution.addTrace(traceError(getClass().getSimpleName(), message, e));
            }
        }
        return resolved;
    }
    // endregion
}
