import SwiftUI
import WebKit

@main
struct ULOTVApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}

struct ContentView: View {
    var body: some View {
        ULOTVWebView()
            .ignoresSafeArea(edges: .bottom)
            .preferredColorScheme(.dark)
    }
}

struct ULOTVWebView: UIViewRepresentable {
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        let web = WKWebView(frame: .zero, configuration: config)
        web.isOpaque = false
        web.backgroundColor = UIColor(red: 7/255, green: 8/255, blue: 13/255, alpha: 1)
        web.scrollView.backgroundColor = web.backgroundColor
        web.allowsBackForwardNavigationGestures = true
        web.load(URLRequest(url: URL(string: "https://ulotv.xo.je/")!))
        return web
    }
    func updateUIView(_ uiView: WKWebView, context: Context) {}
}