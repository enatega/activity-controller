import ExpoModulesCore
import ActivityKit

  import Foundation
import OSLog


final class ActivityUnavailableException: GenericException<Void> {
  override var reason: String { "Live activities are not available on this system." }
}
final class ActivityDataException: GenericException<String> {
  override var reason: String { "Failed to parse Live Activity data: \(param)" }
}


fileprivate struct StartParams: Decodable {

  
         let orderStatus: String
         let estimatedDelivery: String
         let progress: Double  
        
       

     let orderId: String
     let itemName: String
     let totalAmount: String
     let vehicleNumber: String
     let itemImageUrl: String
}

fileprivate struct UpdateParams: Decodable {
  
         let orderStatus: String
         let estimatedDelivery: String
         let progress: Double  
}



public class ActivityControllerModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ActivityController")

    Property("areLiveActivitiesEnabled") {
      if #available(iOS 16.2, *) {
        return ActivityAuthorizationInfo().areActivitiesEnabled
      }
      return false
    }

  AsyncFunction("startLiveActivity") { (rawData: String) async throws -> [String: String] in
    guard #available(iOS 16.2, *) else {
        throw ActivityUnavailableException(())
    }

    let data = Data(rawData.utf8)
    let params: StartParams
    do {
        params = try JSONDecoder().decode(StartParams.self, from: data)
    } catch {
        throw ActivityDataException(rawData)
    }

    guard Activity<DeliveryAttributes>.activities.isEmpty else {
        throw ActivityUnavailableException(())
    }

    guard ActivityAuthorizationInfo().areActivitiesEnabled else {
        throw ActivityUnavailableException(())
    }

    // Create DeliveryAttributes
    let attributes = DeliveryAttributes(
        orderId: params.orderId,
        itemName: params.itemName,
        totalAmount: params.totalAmount,
        vehicleNumber: params.vehicleNumber,
        itemImageUrl: params.itemImageUrl
    )

    // Create initial ContentState
    let contentState = DeliveryAttributes.ContentState(
        orderStatus: params.orderStatus,
        estimatedDelivery: params.estimatedDelivery,
        progress: params.progress
    )

    let activity = try Activity<DeliveryAttributes>.request(
        attributes: attributes,
        contentState: contentState,
        pushType: .token
    )

    // Wait for the first push token
    var tokenString = ""
    for await data in activity.pushTokenUpdates {
        tokenString = data.map { String(format: "%02x", $0) }.joined()
        break
    }

    // Return as a dictionary (JS will receive a native object)
    return [
        "activityId": activity.id,
        "pushToken": tokenString
    ]
}



       AsyncFunction("updateLiveActivity") { (rawData: String) async throws -> Void in
      guard #available(iOS 16.2, *) else {
        throw ActivityUnavailableException(())
      }
      guard let activity = Activity<DeliveryAttributes>.activities.first else {
        throw ActivityUnavailableException(())
      }

      let data = Data(rawData.utf8)
      let params: UpdateParams
      do {
        params = try JSONDecoder().decode(UpdateParams.self, from: data)
      } catch {
        throw ActivityDataException(rawData)
      }

      let updatedState = DeliveryAttributes.ContentState(
        orderStatus: params.orderStatus,
        estimatedDelivery: params.estimatedDelivery,
        progress: params.progress
      )

      await activity.update(using: updatedState)
    }

    AsyncFunction("stopLiveActivity") { () async throws -> Void in
      guard #available(iOS 16.2, *) else {
        throw ActivityUnavailableException(())
      }
      guard let activity = Activity<DeliveryAttributes>.activities.first else {
        throw ActivityUnavailableException(())
      }
      await activity.end(dismissalPolicy: .immediate)
    }

    Function("isLiveActivityRunning") { () -> Bool in
      if #available(iOS 16.2, *) {
        return !Activity<DeliveryAttributes>.activities.isEmpty
      }
      return false
    }


    // MARK: App Group image helpers

// AsyncFunction("saveImageToAppGroup") { (imageUrl: String, promise: Promise) in
//   Task {
//     do {
//       guard let url = URL(string: imageUrl) else {
//         throw GenericException("Invalid image URL")
//       }

//       // 🧩 Define your App Group ID
//       let appGroupId = "group.com.enatega.customerapp"

//       guard let containerUrl = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId) else {
//         throw GenericException("Unable to access App Group container")
//       }

//       // 🗂️ Create (if not exists) a dedicated "WidgetImages" folder in App Group
//       let imagesFolder = containerUrl.appendingPathComponent("WidgetImages", isDirectory: true)
//       try? FileManager.default.createDirectory(at: imagesFolder, withIntermediateDirectories: true)

//       // 🧠 Extract filename from URL and create destination path
//       let lastPath = url.lastPathComponent
//       let fileUrl = imagesFolder.appendingPathComponent(lastPath)

//       // ✅ If file already exists, resolve immediately
//       if FileManager.default.fileExists(atPath: fileUrl.path) {
//         promise.resolve(fileUrl.path)
//         return
//       }

//       // 📥 Download image data
//       let (data, response) = try await URLSession.shared.data(from: url)

//       guard let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 else {
//         throw GenericException("Failed to download image: Invalid response")
//       }

//       // 💾 Save image in "Images" subfolder
//       try data.write(to: fileUrl, options: .atomic)

//       // ✅ Resolve with saved file path
//       promise.resolve(fileUrl.path)

//     } catch {
//       promise.reject("SAVE_IMAGE_ERROR", "Failed to download or save image: \(error.localizedDescription)")
//     }
//   }
// }

AsyncFunction("saveImageToAppGroup") { (imageUrl: String, promise: Promise) in
  Task {
    do {
      print("🟢 Starting saveImageToAppGroup with URL: \(imageUrl)")

      guard let url = URL(string: imageUrl) else {
        print("🔴 Invalid image URL: \(imageUrl)")
        throw GenericException("Invalid image URL")
      }

      let appGroupId = "group.com.enatega.customerapp"
      print("📦 Using App Group ID: \(appGroupId)")

      guard let containerUrl = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId) else {
        print("🔴 Unable to access App Group container for ID: \(appGroupId)")
        throw GenericException("Unable to access App Group container")
      }

      print("📁 App Group container path: \(containerUrl.path)")

      let imagesFolder = containerUrl.appendingPathComponent("WidgetImages", isDirectory: true)
      print("📂 Creating/using images folder at: \(imagesFolder.path)")
      try? FileManager.default.createDirectory(at: imagesFolder, withIntermediateDirectories: true)

      let lastPath = url.lastPathComponent
      print("🧩 Extracted filename: \(lastPath)")

      let fileUrl = imagesFolder.appendingPathComponent(lastPath)
      print("📍 Destination file path: \(fileUrl.path)")

      if FileManager.default.fileExists(atPath: fileUrl.path) {
        print("✅ File already exists at path: \(fileUrl.path)")
        promise.resolve(fileUrl.path)
        return
      }

      print("⬇️ Starting image download from: \(url.absoluteString)")
      let (data, response) = try await URLSession.shared.data(from: url)
      print("📦 Download complete, validating response...")

      guard let httpResponse = response as? HTTPURLResponse else {
        print("🔴 Response is not HTTPURLResponse")
        throw GenericException("Invalid response type")
      }

      print("📡 HTTP Status Code: \(httpResponse.statusCode)")

      guard httpResponse.statusCode == 200 else {
        print("🔴 Download failed with status: \(httpResponse.statusCode)")
        throw GenericException("Failed to download image: Invalid response")
      }

      print("💾 Writing image data to file...")
      try data.write(to: fileUrl, options: .atomic)
      print("✅ Image saved successfully at: \(fileUrl.path)")

      promise.resolve(fileUrl.path)

    } catch {
      print("🚨 Error occurred in saveImageToAppGroup: \(error.localizedDescription)")
      promise.reject("SAVE_IMAGE_ERROR", "Failed to download or save image: \(error.localizedDescription)")
    }
  }
}



AsyncFunction("cleanAppGroupImages") { (maxAgeHours: Double) async throws -> Void in
  let appGroupId = "group.com.enatega.customerapp"

  guard let containerURL = FileManager.default.containerURL(
    forSecurityApplicationGroupIdentifier: appGroupId
  ) else {
    print("❌ Unable to access App Group container.")
    return
  }

  // 🗂️ Target only the "Images" subfolder
  let imagesFolder = containerURL.appendingPathComponent("WidgetImages", isDirectory: true)

  guard FileManager.default.fileExists(atPath: imagesFolder.path) else {
    print("⚠️ No Images folder found — skipping cleanup.")
    return
  }

  let expirySeconds = maxAgeHours * 3600
  let now = Date()
  let fm = FileManager.default

  print("🧹 Starting cleanup — only in Images folder — maxAgeHours: \(maxAgeHours) (\(expirySeconds)s)")

  guard let files = try? fm.contentsOfDirectory(
    at: imagesFolder,
    includingPropertiesForKeys: [.contentModificationDateKey],
    options: [.skipsHiddenFiles]
  ) else {
    print("⚠️ Could not list contents of Images folder.")
    return
  }

  // let allowedExtensions = ["jpg", "jpeg", "png", "webp", "heic"]

  for file in files {
    let fileName = file.lastPathComponent
    let ext = file.pathExtension.lowercased()

    // guard allowedExtensions.contains(ext) else {
    //   print("🚫 Skipping non-image file in Images folder: \(fileName)")
    //   continue
    // }

    do {
      let attrs = try fm.attributesOfItem(atPath: file.path)
      if let modDate = attrs[.modificationDate] as? Date {
        let ageSeconds = now.timeIntervalSince(modDate)

        if ageSeconds > expirySeconds {
          print("🗑️ Removing expired image: \(fileName)")
          try? fm.removeItem(at: file)
        } else {
          print("✅ Keeping recent image: \(fileName)")
        }
      } else {
        print("⚠️ Could not read modification date for: \(fileName)")
      }
    } catch {
      print("❌ Failed to process file \(fileName): \(error.localizedDescription)")
    }
  }

  print("🏁 Image cleanup complete.")
}





  }
}
