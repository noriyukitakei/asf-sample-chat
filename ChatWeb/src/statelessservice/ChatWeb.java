package statelessservice;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.List;

import system.fabric.CancellationToken;
import system.fabric.description.EndpointResourceDescription;
import microsoft.servicefabric.services.communication.runtime.ServiceInstanceListener;
import microsoft.servicefabric.services.runtime.StatelessService;

public class ChatWeb extends StatelessService {

    private static final Logger logger = Logger.getLogger(ChatWeb.class.getName());

    // Chat/ChatApplication/ChatWebPkg/ServiceManifest.xmlに定義してある
    // Webのエンドポイント(ユーザーに公開するポートなど)を定義しているXML要素の
    // 名前を定義する。
    private static final String webEndpointName = "WebEndpoint";
    
    @Override
    protected List<ServiceInstanceListener> createServiceInstanceListeners() {
        // TODO: If your service needs to handle user requests, return the list of ServiceInstanceListeners from here.
    	// Chat/ChatApplication/ChatWebPkg/ServiceManifest.xmlのWebEndpointというname属性を持つ要素に
    	// 記載してある、Stateless Reliable ServiceのWebエンドポイントの情報を取得する。 
        EndpointResourceDescription endpoint = this.getServiceContext().getCodePackageActivationContext().getEndpoint(webEndpointName);
        
        // Chat/ChatApplication/ChatWebPkg/ServiceManifest.xmlのWebEndpointというname属性を持つ要素に定義されている
        // portという属性(各ノードのエンドポイントの公開ポート)を取得する。
        int port = endpoint.getPort();

        // 別途作成したHttpCommunicationListenerを用いて、Stateless ServcieのListenerを作成する。
        List<ServiceInstanceListener> listeners = new ArrayList<ServiceInstanceListener>();
        listeners.add(new ServiceInstanceListener((context) -> new HttpCommunicationListener(context, port)));
        return listeners;
    }

    @Override
    protected CompletableFuture<?> runAsync(CancellationToken cancellationToken) {
        // TODO: Replace the following with your own logic.
        return super.runAsync(cancellationToken);
    }
}
