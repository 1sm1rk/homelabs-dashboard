package de.homelabs.dashboard.design;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import de.homelabs.dashboard.projects.ProjectsViews;

/**
 * The dashboard's own design guide: two addressable pages that document the frontend it
 * ships.
 *
 * <p>{@code /design} is the schematic — the shell zones, the page inventory of the
 * existing pages and, per API object, the wire fields the views read. It is static by
 * design: the page shows STRUCTURE, and a structure that pulled live data would show one
 * state instead.
 *
 * <p>{@code /design/components} is the catalog — every pattern rendered with the markup of
 * the real templates, so a reader copies what the running UI really produces. Parameterized
 * patterns are rendered by calling the module's own fragments; the example content is typed
 * by the module's own view records ({@link DesignSamples}).
 *
 * <p>Neither page reads the projects API and both sit behind the login like every other
 * route: the guide is a module of the shell, registered in the module registry, with no
 * REST base URL of its own.
 */
@Controller
@RequestMapping("/design")
public class DesignController {

    /** The schematic: shell zones, page inventory, wire fields per object. */
    @GetMapping
    public String structure() {
        return "design/structure";
    }

    /**
     * The component catalog. The model attribute names are the fragment contract of the
     * projects templates — the catalog adds no second vocabulary, it feeds the existing
     * fragments their parameters.
     */
    @GetMapping("/components")
    public String components(Model model) {
        model.addAttribute("reference", DesignSamples.PROJECT_REFERENCE);
        model.addAttribute("project", DesignSamples.projectHeader());
        model.addAttribute("breadcrumb", DesignSamples.breadcrumb());
        model.addAttribute("item", DesignSamples.item());
        model.addAttribute("workPackages", DesignSamples.workPackages());
        model.addAttribute("itemStatusOptions", ProjectsViews.ITEM_STATUS_OPTIONS);
        model.addAttribute("statusOptions", ProjectsViews.PROJECT_STATUS_OPTIONS);
        model.addAttribute("samplePackage", DesignSamples.packageRow());
        model.addAttribute("sampleTask", DesignSamples.taskRow());
        model.addAttribute("projects", DesignSamples.projectRows());
        model.addAttribute("sampleLabels", DesignSamples.labels());
        model.addAttribute("projectBadges", DesignSamples.projectBadges());
        model.addAttribute("itemBadges", DesignSamples.itemBadges());
        model.addAttribute("unknownBadge", DesignSamples.unknownBadge());
        model.addAttribute("badRequestProblem", DesignSamples.badRequestProblem());
        model.addAttribute("conflictProblem", DesignSamples.conflictProblem());
        return "design/components";
    }
}
