# WorkManager for Simulated Video Upload

This project demonstrates how to use **WorkManager** in Android to manage background tasks, such as simulating the upload of large files (e.g., videos). While the upload is simulated (without using a network call or Retrofit), the app demonstrates how **WorkManager** can handle background tasks, retries, and device reboots, ensuring that the process continues in the background.

## Features
- Simulate a video upload process using **WorkManager**.
- Handle retries on simulated network failure, device reboot, and app kill.
- Provide constraints such as network availability and charging status.
- Track simulated upload progress with UI updates.

## Prerequisites
- Android Studio
- Kotlin
- WorkManager library
- Jetpack Compose

## Installation

1. **Clone the repository**:

    ```bash
    git clone https://github.com/amit4app/Work-Manager-Video-Upload.git
    ```

2. **Open the project in Android Studio**.
3. **Sync Gradle** to ensure all dependencies are installed.

4. **Run the app** on an emulator or device.

## Usage

### 1. **Simulated Upload Functionality**

This app simulates the upload of a video in the background using **WorkManager**. Rather than performing an actual network call to upload a file to a server, it simulates the upload process by **mocking a file upload** and showing the progress in the UI.

- The app uses **WorkManager** to queue the "upload" task.
- The progress is updated locally in the UI (e.g., a progress bar).
- If the app is killed or the device is restarted, the "upload" will continue automatically due to **WorkManager**'s built-in background task management.

### 2. **Handling Upload**

The app uses **WorkManager** to queue background tasks for simulating the upload. You can observe the progress of the mock upload in the UI, and if the app is killed or the device is rebooted, the simulated upload will resume automatically.

### 3. **Testing**

You can test the functionality by:
- Simulating network loss or device restart to observe how **WorkManager** handles retries.
- Observing the progress bar as the "upload" progresses (mocked with a simulated delay).

### 4. **Future Work**

In the next iteration, we plan to:
- Replace the simulated upload with an actual video upload feature to a real server using `Retrofit` or another networking library.
- Implement more complex upload scenarios, such as pausing and resuming the upload.

## Demo

Here is a demo of the simulated video upload progress:

![Demo GIF](images/work_manager.gif)

This GIF demonstrates how the video upload simulation works. It shows the progress bar updating as the simulated upload progresses. WorkManager ensures that the upload continues even if the app is killed or the device is restarted.

### How It Works:
- **WorkManager** is used to schedule the upload task.
- The upload progress is updated in the UI.
- If the app is closed or the device is restarted, the upload automatically resumes.

## Technologies Used

- **Android**: Kotlin, WorkManager
- **Libraries**: WorkManager

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Feel free to fork this repository, submit issues, and contribute with pull requests. Contributions are welcome to improve the functionality, especially adding new features like:

- Actual video upload to a server.
- Progress bar UI improvements.
- Additional retry policies.

## Acknowledgments

- WorkManager for background tasks.
- Android for providing the platform to demonstrate this.
