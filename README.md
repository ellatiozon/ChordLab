<h1 align="center">🎹 ChordLab: Edge AI Polyphonic Detection System</h1>

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" />
  <img src="https://img.shields.io/badge/Language-Java-ED8B00?style=flat-square&logo=java&logoColor=white" />
  <img src="https://img.shields.io/badge/Edge_AI-TensorFlow_Lite-FF6F00?style=flat-square&logo=tensorflow&logoColor=white" />
  <img src="https://img.shields.io/badge/Vision-Google_MediaPipe-00B2FF?style=flat-square" />
  <img src="https://img.shields.io/badge/Audio-TarsosDSP-8A2BE2?style=flat-square" />
</p>

> A real-time, multimodal Android application designed to provide automated kinesthetic and acoustic feedback for self-taught musicians using localized Convolutional Neural Networks (CNNs).

---

## 👨‍💻 Authorship & Contributions

This repository contains the core software architecture for the ChordLab capstone project. To adhere to academic integrity and accurately reflect individual contributions, the development roles are strictly defined below:

* **Lead System Architect & Core Developer:** [Mikhaella Mari Tiozon](https://github.com/ellatiozon)
  * *Full Backend Development (Java and Python (Model Development))*
  * *AI/Machine Learning Infrastructure Development for Polyphonic Sound Detection and Sensor Fusion*
  * *UI/UX code for core features of Guitar, Ukulele, and Piano.*

* **UI/UX Design:** [Aedrienne Patrice Lalimarmo](https://github.com/map-lali)
  * *Architected the application's wireframes and user flow diagrams*
  * *UI/UX design, establishing the visual hierarchy, color palettes, and interactive elements*
  * *Coded the User Interface (UI)*

* **Front End & Database:** [Alec Vincent Lacap](https://github.com/yuito0910)
  * *Coded the User Interface (UI)*
  * *Developed and managed the local SQLite database to securely track user state, Experience Points (EXP), and progression metrics.*
  * *Executed comprehensive Quality Assurance (QA) and system testing to eliminate bugs*
---

## 🚀 Key Innovations & Architecture

ChordLab was engineered to solve the "cognitive overload" and lack of immediate feedback in traditional solitary music practice. It bypasses cloud latency by running complex signal processing and computer vision entirely on the edge.

### 1. Multimodal Sensor Fusion (Anti-Cheat Engine)
Standard tuning apps are easily tricked by pre-recorded audio. ChordLab requires **simultaneous verification**:
* **Visual:** Google MediaPipe Hand Landmarker tracks 21 3D spatial coordinates to verify physical hand geometry against the fretboard/keys.
* **Acoustic:** AudioRecord captures frequencies to verify pitch. 

### 2. The "Multi-Brain" Dynamic Hot-Swap Architecture
To process complex polyphonic spectrograms without crashing mobile RAM, the system avoids monolithic AI models. Instead, it utilizes a dynamic memory management system:
* The backend seamlessly hot-swaps between three lightweight, specialized `.tflite` models (**Majors [8-class]**, **Minors [8-class]**, and **Accidentals [11-class]**, **Ukulele Open Chords [10-class]**, **Guitar Open Chords [8-class]**) in under 100ms based on the user's current target.

### 3. Audio-to-Image Processing (Seeing Sound)
Relying on 1D audio waveforms is susceptible to background noise and cheap smartphone microphones. ChordLab utilizes **Fast Fourier Transforms (FFT)** via TarsosDSP to convert raw audio into 2D Spectrogram heatmaps. Our custom TensorFlow Lite CNN then "looks" at the sound to classify the chord structure.

### 4. Sliding-Window "Lock-In" Algorithm
To prevent false positives from background noise or fraction-of-a-second acoustic jitter, a custom `LinkedList` sliding-window algorithm ensures multi-frame acoustic agreement before awarding progression points.

---

## 🛠️ Technical Stack
* **Frontend:** Android XML, Material Design Components
* **Backend:** Java (Android SDK)
* **Machine Learning:** TensorFlow Lite (Custom Image Classification)
* **Digital Signal Processing:** TarsosDSP
* **Computer Vision:** Google MediaPipe
* **Local State Management:** SQLite Database

## ⚙️ Build & Installation

To run this project locally:
1. Clone the repository:
   ```bash
   https://github.com/ellatiozon/ChordLab.git
