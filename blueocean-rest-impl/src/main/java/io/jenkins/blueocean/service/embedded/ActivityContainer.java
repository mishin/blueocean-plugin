package io.jenkins.blueocean.service.embedded;

import io.jenkins.blueocean.rest.Reachable;
import io.jenkins.blueocean.rest.hal.Link;
import io.jenkins.blueocean.rest.model.BlueQueueContainer;
import io.jenkins.blueocean.rest.model.BlueRunContainer;
import io.jenkins.blueocean.rest.model.Container;
import io.jenkins.blueocean.rest.model.Resource;
import io.jenkins.blueocean.rest.pageable.Pageables;

import java.util.Iterator;

import static io.jenkins.blueocean.rest.pageable.PagedResponse.Processor.DEFAULT_LIMIT;

/**
 * @author Vivek Pandey
 */
public class ActivityContainer extends Container<Resource> {
    private final BlueQueueContainer queueContainer;
    private final BlueRunContainer runContainer;
    private final Link self;

    public ActivityContainer(BlueQueueContainer queue, BlueRunContainer run, Reachable parent) {
        this.queueContainer = queue;
        this.runContainer = run;
        this.self = parent.getLink().rel("activities");
    }

    @Override
    public Link getLink() {
        return self;
    }

    @Override
    public Resource get(String name) {
        // We can't make this work reliably as queue id/expectedBuildNumber can potentially collide with run ids.
        // Instead of calling /activities/:id client should call _links.self.href for each item in the activities array.
        return null;
    }

    @Override
    public Iterator<Resource> iterator() {
        return iterator(0, DEFAULT_LIMIT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Iterator<Resource> iterator(int start, int limit) {
        return Pageables.combine(start, limit, ((Container)queueContainer).iterator(), ((Container)runContainer).iterator());
    }
}
