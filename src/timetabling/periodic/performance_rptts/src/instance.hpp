#ifndef INSTANCE_HPP
#define INSTANCE_HPP
#include "../../../../core/cpp/include/core.hpp"
#include "RpttsEvent.hpp"
#include "RpttsActivity.hpp"
#include "LineClusters.hpp"
#include "evaluation_function.hpp"
#include "mergeRequest.hpp"
//#include "ip_formulations.hpp"
#include <algorithm>
#include <vector>
#include <map>
#include <queue>
#include <iostream>

class instance{
private:
    LineClusters L;
    Config config;
    AdjacencyListGraph<PeriodicEvent, PeriodicActivity> tempEAN;
    AdjacencyListGraph<RpttsEvent, RpttsActivity> EAN;
    LineCluster lc;
    AdjacencyListGraph<Stop, Link> ptn;
    LinePool lp;
    int time_period;
    long long obj_val = 0;
    std::vector<mergeRequest> mergeRequests;
    std::map<std::pair<int, int>, int> line_to_cluster;
    std::map<std::pair<int, int>, int> time_diff;

public:
    void read_in(void){
        ConfigReader configr = ConfigReader(&config, "Config.cnf", false, "basis/");
        configr.read();
        time_period = config.getIntegerValue("period_length");
        evaluation_function::setTimePeriod(time_period);
        PTNReader ptnr = PTNReader(&ptn, config.getStringValue("default_stops_file"),
                    config.getStringValue("default_edges_file"),
                    !config.getBooleanValue("ptn_is_undirected"));
        ptnr.read();
        // lines
        LinePoolReader lpr = LinePoolReader(&lp, &ptn,
                config.getStringValue("default_lines_file"),
                "",
                !config.getBooleanValue("ptn_is_undirected"),
                true);
        lpr.read();
        // ean
        PeriodicEANReader pEANr = PeriodicEANReader(&tempEAN,
            config.getStringValue("default_activities_periodic_file"),
            config.getStringValue("default_events_periodic_file"));
        pEANr.read();
    }

    void initializeEAN(){
        for (PeriodicEvent &e: tempEAN.getNodes()){
            EAN.addNode(RpttsEvent(e));
        }
        for (PeriodicActivity& a: tempEAN.getEdges()){
            obj_val += (long long) (a.getLowerBound() * a.getNumberOfPassengers());
            RpttsActivity ra = RpttsActivity(a, EAN.getNode(a.getLeftNode()->getId()), EAN.getNode(a.getRightNode()->getId()));
            if (a.getType() == DRIVE || a.getType() == WAIT){
                EAN.getNode(a.getRightNode()->getId())->setPrevEvent(EAN.getNode(a.getLeftNode()->getId()));
                EAN.getNode(a.getRightNode()->getId())->setDurationToPrevEvent(a.getLowerBound());
            }
            EAN.addEdge(ra);
        }
        std::cout << "lower bound: " << obj_val << std::endl;
    }

    void initializeLineClusters(){

        int max_line_id = 0;
        for (Line l: lp.getLines()){
            max_line_id = std::max(max_line_id, l.getId());
        }

        // split line into directed lines. The linepool now consists of directed lines
        for (RpttsEvent re: EAN.getNodes()){
            if (re.getType() == FIX) {
                Line l = Line(2 * max_line_id + 1, true);
                l.setFrequency(1);
                lp.addLine(l);
                EAN.getNode(re.getId())->setLineId(l.getId());
                continue;
            }
            if (re.getPrevEvent() != NULL || re.getLineId() == -1 || lp.getLine(re.getLineId())->getFrequency() == 0)  continue;
            if (re.getStopId() != ((lp.getLine(re.getLineId()))->getLinePath()).getNodes().front().getId()) continue;
            // this is start of a new line
            Line l = Line(re.getLineId() + max_line_id, true);
            l.setFrequency(lp.getLine(re.getLineId())->getFrequency());
            lp.addLine(l);
            // assign new line ids to all events
            RpttsEvent tempre = re;
            while (tempre.getNextEvent() != NULL){
                EAN.getNode(tempre.getId())->setLineId(l.getId());
                tempre = *tempre.getNextEvent();
            }
            EAN.getNode(tempre.getId())->setLineId(l.getId());
        }

        int max_line_cluster_id = 0;

        // create line clusters for each frequency of a line
        for (Line l: lp.getLineConcept()){
            for (int i = 0; i < l.getFrequency(); i++){
                LineCluster lc;
                lc.addLine(RpttsLine(l));
                lc.setId(max_line_cluster_id);
                line_to_cluster[{l.getId(), i+1}] = max_line_cluster_id;
                max_line_cluster_id++;
                L.addLineCluster(lc);
            }
        }
        // assign line cluster ids to events
        for (RpttsEvent e: EAN.getNodes()){
            EAN.getNode(e.getId())->setLineClusterId(line_to_cluster[{e.getLineId(), e.getLineFrequencyRepetition()}]);
        }

        // create super edges
        for (RpttsActivity a: EAN.getEdges()){
            // add for all change activities their x-w pairs
            if (a.getType() == CHANGE){
                if (a.getNumberOfPassengers() == 0) continue;
                a.calculateX();
                L.getLineCluster(a.getLeftNode()->getLineClusterId())->addSuperEdgeValue(a.getX(), a.getNumberOfPassengers(), a.getRightNode()->getLineClusterId());
            }
        }

        // sync edges
        for (RpttsActivity a: EAN.getEdges()){
            if (a.getType() == SYNC){
                if (a.getLeftNode()->getLineClusterId() == a.getRightNode()->getLineClusterId()) {
                    continue;
                }
                mergeRequest m;
                m.setLeftId(a.getLeftNode()->getLineClusterId());
                m.setRightId(a.getRightNode()->getLineClusterId());
                int additional_diff = time_diff[{m.getLeftId(), line_to_cluster[{a.getLeftNode()->getLineId(), a.getLeftNode()->getLineFrequencyRepetition()}]}];
                m.setStartDifference(a.getLowerBound()+additional_diff+a.getLeftNode()->getDurationToStart()-a.getRightNode()->getDurationToStart());
                time_diff[{m.getLeftId(), m.getRightId()}] = a.getLowerBound() + additional_diff + a.getLeftNode()->getDurationToStart()-a.getRightNode()->getDurationToStart();
                time_diff[{m.getRightId(), m.getLeftId()}] = -time_diff[{m.getLeftId(), m.getRightId()}];
                m.setMinVal(0);
                applyMergeRequest(m);
            }
        }
        // circulation edges
        for (RpttsActivity a: EAN.getEdges()){
            if (a.getType() == TURNAROUND){
                if (a.getLeftNode()->getLineClusterId() == a.getRightNode()->getLineClusterId()) continue;
                mergeRequest m;
                m.setLeftId(a.getLeftNode()->getLineClusterId());
                m.setRightId(a.getRightNode()->getLineClusterId());
                int additional_diff = time_diff[{m.getLeftId(), line_to_cluster[{a.getLeftNode()->getLineId(), a.getLeftNode()->getLineFrequencyRepetition()}]}];
                int offset = additional_diff;
                int l_dur = a.getLeftNode()->getDurationToStart() + a.getLowerBound();
                int u_dur = a.getLeftNode()->getDurationToStart() + a.getUpperBound();
                l_dur = (l_dur % time_period + time_period) % time_period;
                u_dur = (u_dur % time_period + time_period) % time_period;
                if (l_dur > 0){
                    if (l_dur <= time_period/2 && (u_dur >= time_period/2 || u_dur < l_dur)){
                        offset += time_period/2;
                    }
                }
                offset += a.getLeftNode()->getDurationToStart() + a.getLowerBound() - a.getRightNode()->getDurationToStart();
                m.setStartDifference(offset);
                time_diff[{m.getLeftId(), m.getRightId()}] = offset;
                time_diff[{m.getRightId(), m.getLeftId()}] = -time_diff[{m.getLeftId(), m.getRightId()}];
                m.setMinVal(0);
                applyMergeRequest(m);
            }
        }
    }

    void applyMergeRequest(mergeRequest m){
        L.mergeClusters(m.getLeftId(), m.getRightId(),m.getStartDifference());

        // assign new line cluster ids to events of right line cluster
        for (RpttsEvent re: EAN.getNodes()){
            if (re.getLineClusterId() == m.getRightId()){
                EAN.getNode(re.getId())->setLineClusterId(m.getLeftId());
            }
        }
        // update objective value
        obj_val += m.getMinVal();
        //std::cout << obj_val << std::endl;

        // store merge request for writing timetable
        mergeRequests.push_back(m);

    }


    void solve_heuristically(int n = 1){

        while (1){
            std::vector<mergeRequest > ans = L.evaluate_clusters(obj_val);

            if (ans.empty()){
                if (L.getNumberOfClusters() != 1){
                    std::cout << "error in evaluation. did not retrieve any mergings" << std::endl;
                }
                else{
                    return;
                }
            }
            for (mergeRequest m: ans){
                applyMergeRequest(m);
            }
            //std::cout << L.getNumberOfClusters() << std::endl;
            if (L.getNumberOfClusters() <= n) break;
        }
    }
    void solve_ip(std::map<int, int> start_times){

        while (1){

            std::vector<mergeRequest > ans = L.evaluate_by_ip(obj_val, time_period, start_times);

            if (ans.empty()){
                if (L.getNumberOfClusters() != 1){
                    std::cout << "error in evaluation. did not retrieve any mergings" << std::endl;
                }
                else{
                    return;
                }
            }
            for (mergeRequest m: ans){
                applyMergeRequest(m);
            }
            if (L.getNumberOfClusters() == 1) break;
        }
    }

    std::map<int, int> write_timetable(){
        //there is only one cluster left
        int last_id = L.getLineClusters().at(0).getId();

        // rollout starting times for every line
        std::map<int, std::vector<std::pair<int, int> > > adj_list;
        for (mergeRequest m: mergeRequests){
            adj_list[m.getLeftId()].push_back({m.getRightId(), m.getStartDifference()});
            adj_list[m.getRightId()].push_back({m.getLeftId(), -m.getStartDifference()});
        }
        std::map<int, bool> vis;
        std::queue<int> q;
        std::map<int, int> start_times;
        start_times[last_id] = 0;
        vis[last_id] = true;
        q.push(last_id);
        while (!q.empty()){
            int curid = q.front(); q.pop();
            for (std::pair<int, int> p: adj_list[curid]){
                int next = p.first;
                int t = p.second;
                if (!vis[next]){
                    start_times[next] = start_times[curid] + t;
                    vis[next] = true;
                    q.push(next);
                }
            }
        }

        //  rollout event times wrt starting times
        for (RpttsEvent re: EAN.getNodes()){
            int original_cluster_id = line_to_cluster[{re.getLineId(), re.getLineFrequencyRepetition()}];
            int tt = (re.getDurationToStart() + start_times[original_cluster_id]) % time_period;
            while (tt < 0) tt += time_period;
            re.setTime(tt);
            tempEAN.getNode(re.getId())->setTime(tt);
        }
        PeriodicTimetableWriter pTTw;
        for (PeriodicActivity a: tempEAN.getEdges()){
            int dur = (a.getRightNode()->getTime() - a.getLeftNode()->getTime() + 100*time_period - a.getLowerBound()) % time_period + a.getLowerBound();
            if (dur < a.getLowerBound() || dur > a.getUpperBound()){
                std::cout << "infeasible activity " << a.getId() << std::endl;
                std::cout << "Duration is " << dur << ", lower bound " << a.getLowerBound() << ", upper bound " << a.getUpperBound() << std::endl;
            }
        }
        pTTw.writeTimetable(tempEAN, config);

        std::cout << obj_val << std::endl;
        return start_times;
    }

};

#endif
