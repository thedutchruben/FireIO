package io.fire.core.loadbalancer.rest;

import io.fire.core.common.objects.VersionInfo;
import io.fire.core.loadbalancer.FireIoBalancer;
import io.fire.core.loadbalancer.servermanager.objects.FireIoNode;
import io.fire.core.server.modules.rest.RestModule;
import io.fire.core.server.modules.rest.enums.ContentType;
import io.fire.core.server.modules.rest.objects.RegistrationEndpoint;
import io.fire.core.server.modules.rest.objects.RegistrationResult;
import lombok.Setter;

import java.util.UUID;

public class RestHandlers {

    @Setter private String clientPassword = null;
    @Setter private String serverPassword = null;

    public RestHandlers(FireIoBalancer balancer) {
        //get HttpHandler
        RestModule rest = (RestModule) balancer.getBalancingServer().getRestModule();

        //change "authentication" endpoint to allow redirects
        rest.getHttpHandler().setRestClientRegestration(new RegistrationEndpoint(givenPassword -> {
            if (clientPassword != null) {
                if (!clientPassword.equals(givenPassword)) {
                    return new RegistrationResult(false, "fail-auth", ContentType.PLAINTEXT);
                }
            }

            FireIoNode node = balancer.getServerManager().getAvailableServer();
            String out = "";
            if (node == null) {
                out = "redirect=none";
            } else {
                out = "redirect=" + node.getHost() + ":" + node.getPort();
            }
            return new RegistrationResult(true, out, ContentType.PLAINTEXT);
        }));

        //auth adress for servers
        balancer.getBalancingServer().registerEndpoint("/fireio/balancer/list",  req -> {
            final String[] out = {""};
            balancer.getServerManager().getNodes().values().forEach(n -> {
                out[0] += "id="+n.getUuid().toString() + ",state="+n.getState() +",clients="+n.getConnections()+",httpinteractions="+n.getRestInteractions()+"\n";
            });
            return out[0];
        });

        balancer.getBalancingServer().registerEndpoint("/", req -> {
            FireIoNode n = balancer.getServerManager().getAvailableEndpoint();
            if (n == null) {
                return "no-server-available";
            }
            n.restInteractions++;
            return "http-redirect:http://" + n.getHost() + ":" + n.getPort() + req.getURL();
        });

        //auth adress for servers
        balancer.getBalancingServer().registerEndpoint("/fireio/balancer/register/?port/?password",  req -> {
            if (serverPassword != null) if (!serverPassword.equals(req.getVariable("password"))) return "fail-auth";
            //do shit
            UUID id = balancer.getBalancingServer().getClientModule().registerConnection().getId();
            String out = id.toString() + "INFO:" + new VersionInfo().toString();
            //register new expecting node
            FireIoNode node = balancer.getServerManager().create(id);
            node.setPort(Integer.valueOf(req.getVariable("port")));
            node.setHost(req.getRequester().getHostString());
            return out;
        });
    }

}
