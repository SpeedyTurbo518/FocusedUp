import SwiftUI
import UIKit
import Shared

@main
struct FocusedUpApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .preferredColorScheme(.dark)
                .background(Color.black)
        }
    }
}

/// Hosts the shared Compose Multiplatform UI (same screens as Android and desktop).
struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
