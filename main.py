from kivy.app import App
from kivy.lang import Builder


KV = """
BoxLayout:
    orientation: "vertical"
    padding: dp(24)
    spacing: dp(16)

    Label:
        text: "Python Android App"
        font_size: "28sp"
        bold: True
        size_hint_y: None
        height: self.texture_size[1]

    Label:
        text: "APK project scaffold is ready."
        font_size: "18sp"

    Button:
        text: "Tap me"
        size_hint_y: None
        height: dp(56)
        on_release: status.text = "Button tapped"

    Label:
        id: status
        text: "Ready"
        font_size: "16sp"
        size_hint_y: None
        height: self.texture_size[1]
"""


class AndroidPythonApp(App):
    def build(self):
        self.title = "Python APK"
        return Builder.load_string(KV)


if __name__ == "__main__":
    AndroidPythonApp().run()
