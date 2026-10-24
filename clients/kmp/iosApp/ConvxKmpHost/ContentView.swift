import SwiftUI
import UIKit
import ConvxShared

struct ContentView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        IosMusicControllerKt.iosMusicController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
