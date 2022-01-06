package io.openex.rest.exercise;

import io.openex.database.model.*;
import io.openex.database.repository.*;
import io.openex.database.specification.ComcheckSpecification;
import io.openex.database.specification.DryRunSpecification;
import io.openex.database.specification.ExerciseLogSpecification;
import io.openex.injects.base.AttachmentContent;
import io.openex.injects.base.InjectAttachment;
import io.openex.rest.exercise.export.ExerciseFileExport;
import io.openex.rest.exercise.export.ExerciseFileImport;
import io.openex.rest.exercise.export.ExerciseImport;
import io.openex.rest.exercise.form.*;
import io.openex.rest.helper.RestBehavior;
import io.openex.service.DryrunService;
import io.openex.service.FileService;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PostAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.activation.MimetypesFileTypeMap;
import javax.annotation.security.RolesAllowed;
import javax.servlet.http.HttpServletResponse;
import javax.transaction.Transactional;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static io.openex.config.AppConfig.currentUser;
import static io.openex.database.model.User.ROLE_ADMIN;
import static io.openex.database.model.User.ROLE_USER;
import static io.openex.helper.DatabaseHelper.resolveRelation;
import static io.openex.helper.DatabaseHelper.updateRelation;
import static java.io.File.createTempFile;
import static java.time.Instant.now;

@RestController
@RolesAllowed(ROLE_USER)
public class ExerciseApi<T> extends RestBehavior {

    private final static String EXPORT_ENTRY_EXERCISE = "Exercise";
    private final static String EXPORT_ENTRY_ATTACHMENT = "Attachment";

    // region repositories
    private TagRepository tagRepository;
    private DocumentRepository documentRepository;
    private ExerciseRepository exerciseRepository;
    private ExerciseLogRepository exerciseLogRepository;
    private DryRunRepository dryRunRepository;
    private ComcheckRepository comcheckRepository;
    private GroupRepository groupRepository;
    private AudienceRepository audienceRepository;
    private InjectRepository<T> injectRepository;
    // endregion

    // region services
    private DryrunService<T> dryrunService;
    private FileService fileService;
    // endregion

    // region setters
    @Autowired
    public void setInjectRepository(InjectRepository<T> injectRepository) {
        this.injectRepository = injectRepository;
    }

    @Autowired
    public void setFileService(FileService fileService) {
        this.fileService = fileService;
    }

    @Autowired
    public void setAudienceRepository(AudienceRepository audienceRepository) {
        this.audienceRepository = audienceRepository;
    }

    @Autowired
    public void setTagRepository(TagRepository tagRepository) {
        this.tagRepository = tagRepository;
    }

    @Autowired
    public void setDocumentRepository(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Autowired
    public void setDryrunService(DryrunService<T> dryrunService) {
        this.dryrunService = dryrunService;
    }

    @Autowired
    public void setGroupRepository(GroupRepository groupRepository) {
        this.groupRepository = groupRepository;
    }

    @Autowired
    public void setComcheckRepository(ComcheckRepository comcheckRepository) {
        this.comcheckRepository = comcheckRepository;
    }

    @Autowired
    public void setDryRunRepository(DryRunRepository dryRunRepository) {
        this.dryRunRepository = dryRunRepository;
    }

    @Autowired
    public void setExerciseLogRepository(ExerciseLogRepository exerciseLogRepository) {
        this.exerciseLogRepository = exerciseLogRepository;
    }

    @Autowired
    public void setExerciseRepository(ExerciseRepository exerciseRepository) {
        this.exerciseRepository = exerciseRepository;
    }
    // endregion

    // region logs
    @GetMapping("/api/exercises/{exercise}/logs")
    public Iterable<ExerciseLog> logs(@PathVariable String exercise) {
        return exerciseLogRepository.findAll(ExerciseLogSpecification.fromExercise(exercise));
    }

    @PostMapping("/api/exercises/{exerciseId}/logs")
    public ExerciseLog createLog(@PathVariable String exerciseId,
                                 @Valid @RequestBody LogCreateInput createLogInput) {
        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
        ExerciseLog log = new ExerciseLog();
        log.setUpdateAttributes(createLogInput);
        log.setExercise(exercise);
        log.setUser(currentUser());
        log.setDate(new Date());
        return exerciseLogRepository.save(log);
    }
    // endregion

    // region dryruns
    @GetMapping("/api/exercises/{exerciseId}/dryruns")
    public Iterable<Dryrun> dryruns(@PathVariable String exerciseId) {
        return dryRunRepository.findAll(DryRunSpecification.fromExercise(exerciseId));
    }

    @PostMapping("/api/exercises/{exerciseId}/dryruns")
    public Dryrun createDryrun(@PathVariable String exerciseId,
                               @Valid @RequestBody DryRunCreateInput input) {
        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
        return dryrunService.provisionDryrun(exercise, input.getSpeed());
    }

    @GetMapping("/api/exercises/{exerciseId}/dryruns/{dryrunId}")
    public Dryrun dryrun(@PathVariable String exerciseId,
                         @PathVariable String dryrunId) {
        Specification<Dryrun> filters = DryRunSpecification
                .fromExercise(exerciseId).and(DryRunSpecification.id(dryrunId));
        return dryRunRepository.findOne(filters).orElseThrow();
    }

    @DeleteMapping("/api/exercises/{exerciseId}/dryruns/{dryrunId}")
    public void deleteDryrun(@PathVariable String exerciseId, @PathVariable String dryrunId) {
        dryRunRepository.deleteById(dryrunId);
    }

    @GetMapping("/api/exercises/{exerciseId}/dryruns/{dryrunId}/dryinjects")
    public List<DryInject<?>> dryrunInjects(@PathVariable String exerciseId,
                                            @PathVariable String dryrunId) {
        return dryrun(exerciseId, dryrunId).getInjects();
    }
    // endregion

    // region comchecks
    @GetMapping("/api/exercises/{exercise}/comchecks")
    public Iterable<Comcheck> comchecks(@PathVariable String exercise) {
        return comcheckRepository.findAll(ComcheckSpecification.fromExercise(exercise));
    }

    @GetMapping("/api/exercises/{exercise}/comchecks/{comcheck}")
    public Comcheck comcheck(@PathVariable String exercise,
                             @PathVariable String comcheck) {
        Specification<Comcheck> filters = ComcheckSpecification
                .fromExercise(exercise).and(ComcheckSpecification.id(comcheck));
        return comcheckRepository.findOne(filters).orElseThrow();
    }

    @GetMapping("/api/exercises/{exercise}/comchecks/{comcheck}/statuses")
    public List<ComcheckStatus> comcheckStatuses(@PathVariable String exercise,
                                                 @PathVariable String comcheck) {
        return comcheck(exercise, comcheck).getComcheckStatus();
    }
    // endregion

    // region exercises
    @PostMapping("/api/exercises")
    public Exercise createExercise(@Valid @RequestBody ExerciseCreateInput input) {
        Exercise exercise = new Exercise();
        exercise.setUpdateAttributes(input);
        exercise.setTags(fromIterable(tagRepository.findAllById(input.getTagIds())));
        return exerciseRepository.save(exercise);
    }

    @PutMapping("/api/exercises/{exerciseId}/start")
    @PostAuthorize("isExercisePlanner(#exerciseId)")
    public Exercise startExercise(@PathVariable String exerciseId) {
        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
        exercise.setStart(Date.from(now().plus(10, ChronoUnit.SECONDS))); // Start in 10 sec
        return exerciseRepository.save(exercise);
    }

    @PutMapping("/api/exercises/{exerciseId}/information")
    @PostAuthorize("isExercisePlanner(#exerciseId)")
    public Exercise updateExerciseInformation(@PathVariable String exerciseId,
                                              @Valid @RequestBody ExerciseUpdateInfoInput input) {
        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
        exercise.setUpdateAttributes(input);
        return exerciseRepository.save(exercise);
    }

    @PutMapping("/api/exercises/{exerciseId}/tags")
    @PostAuthorize("isExercisePlanner(#exerciseId)")
    public Exercise updateExerciseTags(@PathVariable String exerciseId,
                                       @Valid @RequestBody ExerciseUpdateTagsInput input) {
        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
        exercise.setTags(fromIterable(tagRepository.findAllById(input.getTagIds())));
        return exerciseRepository.save(exercise);
    }

    @PutMapping("/api/exercises/{exerciseId}/image")
    @PostAuthorize("isExercisePlanner(#exerciseId)")
    public Exercise updateExerciseImage(@PathVariable String exerciseId,
                                        @Valid @RequestBody ExerciseUpdateImageInput input) {
        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
        exercise.setImage(documentRepository.findById(input.getImageId()).orElse(null));
        return exerciseRepository.save(exercise);
    }

    @DeleteMapping("/api/exercises/{exerciseId}")
    @PostAuthorize("isExercisePlanner(#exerciseId)")
    public void deleteExercise(@PathVariable String exerciseId) {
        exerciseRepository.deleteById(exerciseId);
    }

    @GetMapping("/api/exercises/{exerciseId}")
    @PostAuthorize("isExerciseObserver(#exerciseId)")
    public Exercise exercise(@PathVariable String exerciseId) {
        return exerciseRepository.findById(exerciseId).orElseThrow();
    }

    @GetMapping("/api/exercises")
    @RolesAllowed(ROLE_USER)
    public Iterable<Exercise> exercises() {
        return currentUser().isAdmin() ?
                exerciseRepository.findAll() :
                exerciseRepository.findAllGranted(currentUser().getId());
    }
    // endregion

    // region import/export
    private void handleDataImport(InputStream inputStream) throws IOException {
        @SuppressWarnings("unchecked")
        ExerciseFileImport<T> dataImport = mapper.readValue(inputStream, ExerciseFileImport.class);
        // Create tags
        Map<String, Tag> tagMap = fromIterable(tagRepository.findAll()).stream().collect(
                Collectors.toMap(Tag::getName, Function.identity()));
        List<Tag> tagsToCreate = dataImport.getTags().stream()
                .filter(tagImport -> tagMap.get(tagImport.getName()) == null)
                .map(tagImport -> {
                    Tag tag = new Tag();
                    tag.setUpdateAttributes(tagImport);
                    return tag;
                }).toList();
        List<Tag> createdTags = fromIterable(tagRepository.saveAll(tagsToCreate));
        List<Tag> tagsToRemap = dataImport.getTags().stream()
                .filter(tagImport -> tagMap.get(tagImport.getName()) != null)
                .map(tagImport -> tagMap.get(tagImport.getName())).toList();
        List<Tag> exerciseTags = Stream.concat(createdTags.stream(), tagsToRemap.stream()).toList();
        // Create exercise
        ExerciseImport inputExercise = dataImport.getExercise();
        Exercise exerciseToSave = new Exercise();
        exerciseToSave.setUpdateAttributes(inputExercise);
        exerciseToSave.setTags(exerciseTags);
        exerciseToSave.setImage(resolveRelation(inputExercise.getImage(), documentRepository));
        Exercise exercise = exerciseRepository.save(exerciseToSave);
        // Create audiences
        List<Audience> audiences = dataImport.getAudiences().stream()
                .map(audienceImport -> {
                    Audience audience = new Audience();
                    audience.setUpdateAttributes(audienceImport);
                    audience.setExercise(exercise);
                    // audience.setUsers();
                    return audience;
                }).toList();
        audienceRepository.saveAll(audiences);
        // Create injects
        List<Inject<T>> injects = dataImport.getInjects().stream().map(injectInput -> {
            Inject<T> inject = injectInput.toInject();
            inject.setExercise(exercise);
            inject.setDependsOn(resolveRelation(injectInput.getDependsOn(), injectRepository));
            inject.setAudiences(fromIterable(audienceRepository.findAllById(injectInput.getAudiences())));
            // inject.setUser(resolveRelation(injectInput.geUser(), userRepository));
            return inject;
        }).toList();
        injectRepository.saveAll(injects);
    }

    @GetMapping("/api/exercises/{exerciseId}/export")
    @PostAuthorize("isExerciseObserver(#exerciseId)")
    public void exerciseExport(@PathVariable String exerciseId, HttpServletResponse response) throws IOException {
        ExerciseFileExport importExport = new ExerciseFileExport();
        Exercise exercise = exerciseRepository.findById(exerciseId).orElseThrow();
        // Build the response
        String zipName = exercise.getName() + "_" + now().toString() + ".zip";
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + zipName);
        response.addHeader(HttpHeaders.CONTENT_TYPE, "application/zip");
        response.setStatus(HttpServletResponse.SC_OK);
        // Build the export
        importExport.setExercise(exercise);
        List<Tag> exerciseTags = new ArrayList<>(exercise.getTags());
        // Audiences
        List<Audience> audiences = exercise.getAudiences();
        importExport.setAudiences(audiences);
        exerciseTags.addAll(audiences.stream().flatMap(audience -> audience.getTags().stream()).toList());
        // Injects
        List<Inject<?>> injects = exercise.getInjects();
        exerciseTags.addAll(injects.stream().flatMap(inject -> inject.getTags().stream()).toList());
        importExport.setInjects(injects);
        // Tags
        importExport.setTags(exerciseTags);
        // Documents
        List<String> documentIds = injects.stream()
                .map(Injection::getContent)
                .filter(content -> content instanceof AttachmentContent)
                .map(content -> (AttachmentContent) content)
                .flatMap(attachmentContent -> attachmentContent.getAttachments().stream())
                .map(InjectAttachment::getId).toList();
        // Build the zip
        ZipOutputStream zipExport = new ZipOutputStream(response.getOutputStream());
        ZipEntry zipEntry = new ZipEntry(exercise.getName() + ".json");
        zipEntry.setComment(EXPORT_ENTRY_EXERCISE);
        zipExport.putNextEntry(zipEntry);
        zipExport.write(mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(importExport));
        zipExport.closeEntry();
        documentIds.forEach(docId -> {
            Document doc = documentRepository.findById(docId).orElseThrow();
            Optional<InputStream> docStream = fileService.getFile(doc.getName());
            if (docStream.isPresent()) {
                try {
                    ZipEntry zipDoc = new ZipEntry(doc.getName());
                    zipDoc.setComment(EXPORT_ENTRY_ATTACHMENT);
                    byte[] data = docStream.get().readAllBytes();
                    zipExport.putNextEntry(zipDoc);
                    zipExport.write(data);
                    zipExport.closeEntry();
                } catch (IOException e) {
                    // Cant add to zip
                    e.printStackTrace();
                }
            }
        });
        zipExport.finish();
        zipExport.close();
    }

    @Transactional
    @PostMapping("/api/exercises/import")
    @RolesAllowed(ROLE_ADMIN)
    public void exerciseImport(@RequestPart("file") MultipartFile file) throws Exception {
        // 01. Use a temporary file.
        File tempFile = createTempFile("openex-import-" + now().getEpochSecond(), ".zip");
        FileUtils.copyInputStreamToFile(file.getInputStream(), tempFile);
        // 02. Use this file to load zip with information
        ZipFile zipFile = new ZipFile(tempFile);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        // Iter on each element to process it.
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String entryType = entry.getComment();
            InputStream zipInputStream = zipFile.getInputStream(entry);
            switch (entryType) {
                case EXPORT_ENTRY_EXERCISE -> handleDataImport(zipInputStream);
                case EXPORT_ENTRY_ATTACHMENT -> {
                    String entryName = entry.getName();
                    String contentType = new MimetypesFileTypeMap().getContentType(entryName);
                    fileService.uploadFile(entryName, zipInputStream, entry.getSize(), contentType);
                }
                default -> throw new UnsupportedOperationException("Cant import type " + entryType);
            }
        }
        // 03. Delete the temporary file
        //noinspection ResultOfMethodCallIgnored
        tempFile.delete();
    }
    // endregion
}
