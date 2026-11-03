package de.homelabs.dashboard;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import de.homelabs.dashboard.projects.ActivityRecord;
import de.homelabs.dashboard.projects.AttachmentRecord;
import de.homelabs.dashboard.projects.CommentRecord;
import de.homelabs.dashboard.projects.LabelRecord;
import de.homelabs.dashboard.projects.ProjectRecord;
import de.homelabs.dashboard.projects.ProjectsViews;
import de.homelabs.dashboard.projects.ProjectsViews.StatusOption;
import de.homelabs.dashboard.projects.TaskRecord;
import de.homelabs.dashboard.projects.WorkPackageRecord;
import de.homelabs.dashboard.testsupport.StubClientRegistrationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The design guide renders both of its zones, and it renders them from the code it
 * documents: the schematic is compared against the wire records of the projects module, and
 * the catalog is fed through the module's own fragments.
 *
 * <p>No projects API is configured for this test — a page that read live data would fail
 * here, which is exactly the property the guide claims: it shows patterns, not states.
 *
 * <p>The login gate is the production one (proven by {@link DashboardBootIT}); this test
 * uses the rendering seam the other rendering tests use.
 */
@SpringBootTest
@Import({ StubClientRegistrationRepository.class, ShellRenderingIT.PermitAllConfiguration.class })
class DesignGuideIT {

    /**
     * Every full record the projects module reads off the wire, with the anchor of its
     * section on the schematic page.
     */
    private static final Map<Class<?>, String> WIRE_RECORDS = Map.of(
            ProjectRecord.class, "object-project",
            WorkPackageRecord.class, "object-work-package",
            TaskRecord.class, "object-task",
            CommentRecord.class, "object-comment",
            ActivityRecord.class, "object-activity",
            AttachmentRecord.class, "object-attachment",
            LabelRecord.class, "object-label");

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MessageSource messageSource;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void rendersTheSchematicWithBothZonesOfTheGuide() throws Exception {
        mockMvc.perform(get("/design").locale(Locale.GERMANY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Grundstruktur · homelabs</title>")))
                .andExpect(content().string(containsString("Grundstruktur des Dashboards")))
                // the rail entry comes from the module registry, like every other module
                .andExpect(content().string(containsString("href=\"/design\"")))
                .andExpect(content().string(containsString(">Design</a>")))
                // both zones of the guide are addressable through the module's subnav
                .andExpect(content().string(containsString("class=\"subnav\"")))
                .andExpect(content().string(containsString("href=\"/design\"")))
                .andExpect(content().string(containsString("href=\"/design/components\"")));
    }

    @Test
    void rendersTheShellZonesAndThePageInventory() throws Exception {
        String html = htmlOf("/design");

        assertThat(html)
                .contains("class=\"schematic\"")
                .contains("schematic__zone--rail")
                .contains(">rail<")
                .contains(">topbar<")
                .contains(">subnav<")
                .contains(">content<")
                // page inventory: the routes that exist today
                .contains("/projects/{reference}")
                .contains("/projects/{reference}/work-packages/{itemReference}")
                .contains("/projects/{reference}/tasks/{itemReference}");
    }

    /**
     * The living half of the schematic: every wire field of every full record appears as a
     * field cell. A renamed or added record component turns this test red instead of
     * leaving a stale table on the page.
     */
    @Test
    void listsEveryWireFieldOfTheFullRecords() throws Exception {
        String html = htmlOf("/design");

        for (Map.Entry<Class<?>, String> record : WIRE_RECORDS.entrySet()) {
            assertThat(html).as("object section of %s", record.getKey().getSimpleName())
                    .contains("id=\"" + record.getValue() + "\"");
            for (String field : wireFields(record.getKey())) {
                assertThat(html).as("wire field %s of %s", field, record.getKey().getSimpleName())
                        .contains("<td class=\"mono\">" + field + "</td>");
            }
        }
    }

    /** Every status of the module has its badge on the catalog page, with its own label. */
    @Test
    void rendersABadgeForEveryStatusOfTheModule() throws Exception {
        String html = htmlOf("/design/components");

        for (StatusOption option : statuses()) {
            String label = messageSource.getMessage(option.labelKey(), null, Locale.GERMAN);

            assertThat(html).as("badge of status %s", option.value())
                    .contains(">" + label + "</span>");
        }
        assertThat(html).as("fallback badge of an unknown value").contains(">Unbekannt</span>");
    }

    @Test
    void rendersTheComponentCatalogWithTheMarkupOfTheRealTemplates() throws Exception {
        String html = htmlOf("/design/components");

        assertThat(html)
                // page header, project header (fragment), breadcrumb (fragment)
                .contains("class=\"page__header\"")
                .contains("class=\"breadcrumb__list\"")
                .contains("class=\"chips\"")
                .contains("class=\"chip\"")
                // cards, tables, rows
                .contains("class=\"card__header\"")
                .contains("class=\"card card--table\"")
                .contains("class=\"tree__package\"")
                .contains("class=\"tree__task\"")
                // the plan row keeps its real editors
                .contains("class=\"status-select\"")
                .contains("name=\"assignee_sub\"")
                .contains("name=\"sort_order\"")
                // the record and the editor grid, description HTML included
                .contains("class=\"record\"")
                .contains("class=\"editors\"")
                .contains("class=\"editor editor--wide\"")
                .contains("name=\"description_html\"")
                .contains("name=\"work_package_id\"")
                // buttons and notices
                .contains("button--ghost")
                .contains("button--danger")
                .contains("class=\"alert\"")
                .contains("class=\"problem__field\"")
                .contains("short_code")
                .contains("class=\"problem__suggestions\"")
                .contains("class=\"field-error\"")
                .contains("class=\"empty\"");
    }

    /**
     * The catalog is inert: the fragment-backed specimens disable htmx inside themselves,
     * and no form on the page posts anywhere — pressing a control can neither read nor
     * write.
     */
    @Test
    void theCatalogIsInert() throws Exception {
        mockMvc.perform(get("/design/components").locale(Locale.GERMANY))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("hx-disable")))
                .andExpect(content().string(not(containsString("method=\"post\""))))
                .andExpect(content().string(not(containsString("action=\"/projects"))));
    }

    @Test
    void translatesTheGuideIntoEnglish() throws Exception {
        mockMvc.perform(get("/design/components").param("lang", "en"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<html lang=\"en\"")))
                .andExpect(content().string(containsString("<title>Components · homelabs</title>")))
                .andExpect(content().string(containsString("Component catalog")))
                .andExpect(content().string(containsString(">Components</a>")));
    }

    private String htmlOf(String path) throws Exception {
        return mockMvc.perform(get(path).locale(Locale.GERMANY))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static List<StatusOption> statuses() {
        return Stream.concat(ProjectsViews.PROJECT_STATUS_OPTIONS.stream(),
                ProjectsViews.ITEM_STATUS_OPTIONS.stream()).toList();
    }

    /** The wire names of one record, read from the annotations the module declares them with. */
    private static List<String> wireFields(Class<?> record) {
        assertThat(record.isRecord()).as("%s is a record", record.getSimpleName()).isTrue();
        return Arrays.stream(record.getRecordComponents())
                .map(DesignGuideIT::wireField)
                .toList();
    }

    private static String wireField(RecordComponent component) {
        JsonProperty property = component.getAccessor().getAnnotation(JsonProperty.class);

        assertThat(property).as("explicit wire name of %s", component.getName()).isNotNull();
        return property.value();
    }
}
