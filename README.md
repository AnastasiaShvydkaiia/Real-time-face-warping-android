
# <img src="./img/app-icon.png" width="200" height="200"> *Reflekt - a practical exploration of computer vision techniques in constrained mobile environments.*

**Reflekt** is an implementation of facial image warping techniques on mobile devices. This application demonstrates three computer vision methodologies working in tandem with MediaPipe Face Mesh to create real-time expressive filters, providing insights into algorithm performance, optimization strategies, and visual quality trade-offs.

<!-- Tech Stack -->
## 🛠️ Technology Stack

| Layer | Technology | Purpose |
|-------|------------|---------|
| **UI** | Jetpack Compose | Modern declarative UI |
| **Vision** | MediaPipe Face Landmarker | Facial feature detection |
| **Warping** | IDW, PAT, MLS Algorithmы | Smooth face transformation |

> [!NOTE]
> This is a CPU-only implementation, therefore the processing is slow. Future improvements could include GPU acceleration for faster performance.

## 🎥 Demo

<img src="./img/video.gif" width="300"/>

## Project Overview

The app applies facial filters by manipulating facial landmarks detected with MediaPipe FaceMesh. The main filters include:

- Glasses Filter: Overlays glasses images based on landmarks; rotates according to the face’s roll angle.

- Sad Filter: Moves corners of the mouth down and adjusts eyebrows.

- Young Face Filter: Exaggerates features for a young face effect.

<img src="./img/filters2.jpg" width="800"/>\
<img src="./img/filters3.jpg" width="800"/>\
<img src="./img/filters.jpg" width="800"/>\

Three warping algorithms were tested:

- Inverse Distance Weighting (IDW): Primary method used in the final prototype. Smoothly interpolates landmark movements across the face. Chosen for its visual quality and simplicity.

- Moving Least Squares (MLS): Produces high-quality warping but is slower and more computationally heavy.

- Piecewise Affine Triangulation (PAT): Demonstrates the highest FPS. However, this performance advantage comes at the cost of visible triangle boundary seams.

Table below summarizes the key characteristics of the three warping algorithms implemented in this project:

|Algorithm|	Mathematical Basis|	Scope|	Representation|	
|---|---|---|---|
IDW|	A Two-Dimensional Interpolation Function for Irregularly-Spaced Data (Shepard, 1968)|	Global|	Control Point|	
MLS|	Image deformation using moving least squares (Schaefer et al., 2006)|	Local|	Control Point|	
PAT|	Piecewise linear mapping functions for image registration (Goshtasby, 1986)|	Local|	Mesh|	


### 🎭 Visual Comparison

Sad face filter implementation using different warping techniques: 

![Comparison](./img/warps.png)

### ⚡ Performance metrics
| Technique | FPS |ms/frame|Memory usage (MB)|Artifacts|
|-----------|-----|--------|-----------------|---------|
|PAT        |15-20|50-100  |10-15  |Triangle boundary seams|
|MLS        |0-1  |6000+   |10-15  |None visible (similar to IDW)|
|IDW        |5-10 |100-200 |10-15 |Slight global smoothing|

## 🛠️ Running Application
### Prerequisites 
- Android Studio Otter (2024.1+)
- Android SDK 34
- Android device running Android 15 (API 34)
- JDK 17 
### Setup
  1. Clone the repo or download the zip folder
  ```git clone https://github.com/AnastasiaShvydkaiia/Real-time-face-warping-android.git```
  2. Open Android Studio and select Open an existing project.
  3. Wait for Gradle sync to complete.
### Run
  1. Connect a physical Android device.
  3. Click Run.
  4. On first launch, grant camera permissions when prompted.
> [!NOTE]
> To run the application on a physical device, enable Developer Options and USB debugging on the device.
<div align="center">
Built with ❤️ by Anastasiia Shvydkaia



