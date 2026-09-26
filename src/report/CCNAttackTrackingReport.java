/*
 * Copyright 2010 Aalto University, ComNet
 * Released under GPLv3. See LICENSE.txt for details.
 */
package report;

import java.util.List;
import java.util.Map;

import core.DTNHost;
import core.SimScenario;
import routing.CCNRouter;

/**
 * Reports CCN attack tracking information collected by CCNRouter.
 */
public class CCNAttackTrackingReport extends Report {

    /**
     * Constructor.
     */
    public CCNAttackTrackingReport() {
        init();
    }

    @Override
    protected void init() {
        super.init();
        write("# CCN attack tracking report");
    }

    @Override
    public void done() {

        List<DTNHost> hosts = SimScenario.getInstance().getHosts();

        for (DTNHost host : hosts) {

            if (!(host.getRouter() instanceof CCNRouter)) {
                continue;
            }

            CCNRouter router = (CCNRouter) host.getRouter();

            if (!host.toString().startsWith(CCNRouter.ATTACKER_MODE)) {
                continue;
            }

            write("observer: " + host);

            write("tracked_consumers:");

            Map<String, List<String>> consumers = router.getTrackedConsumers();

            for (Map.Entry<String, List<String>> entry : consumers.entrySet()) {

                for (String consumer : entry.getValue()) {
                    write(entry.getKey() + " " + consumer);
                }
            }

            write("tracked_producer_candidates:");

            Map<String, List<String>> producers = router.getTrackedProducerCandidates();

            for (Map.Entry<String, List<String>> entry : producers.entrySet()) {

                for (String producer : entry.getValue()) {
                    write(entry.getKey() + " " + producer);
                }
            }

            write("tracking_history:");

            Map<String, List<String>> history = router.getTrackingHistory();

            for (Map.Entry<String, List<String>> entry : history.entrySet()) {

                for (String observation : entry.getValue()) {
                    write(entry.getKey() + " " + observation);
                }
            }

            write("");
        }

        super.done();
    }
}