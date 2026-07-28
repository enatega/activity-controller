import Foundation
import ActivityKit
import OSLog
import React


// MARK: - JSON param structs used by the module
fileprivate struct ActivityStateParams: Decodable {
  let schemaVersion: Int
  let status: String
  let estimatedArrivalEpoch: Int64
  let etaUpdatedAtEpoch: Int64
  let riderName: String
  let riderPhone: String
  let language: String
}

fileprivate struct StartParams: Decodable {
  let orderId: String
  let displayOrderId: String
  let state: ActivityStateParams
}

fileprivate struct UpdateParams: Decodable {
  let orderId: String
  let terminal: Bool?
  let state: ActivityStateParams
}

// MARK: - React Native Bridge
@objc(ActivityController) // name exposed to JS: NativeModules.ActivityController
class ActivityController: RCTEventEmitter {

  // Optional: explicit moduleName (some RN setups expect it)
  @objc
  override static func moduleName() -> String! {
    return "ActivityController"
  }

  // Tell RN whether module requires main queue. This module does not.
  @objc
  override static func requiresMainQueueSetup() -> Bool {
    return false
  }

  private let logger = Logger(subsystem: Bundle.main.bundleIdentifier ?? "ActivityController", category: "ActivityController")
  private let tokenEventQueue = DispatchQueue(
    label: "com.enatega.activitycontroller.token-events"
  )
  private var hasEventListeners = false
  private var pendingTokenEvents: [[String: String]] = []
  private var tokenTasks: [String: Task<Void, Never>] = [:]

  override func supportedEvents() -> [String]! {
    ["LiveActivityTokenUpdated"]
  }

  override func startObserving() {
    let queuedEvents = tokenEventQueue.sync {
      hasEventListeners = true
      let events = pendingTokenEvents
      pendingTokenEvents.removeAll()
      return events
    }
    logger.log("Live Activity token listener started; queuedEvents=\(queuedEvents.count)")
    queuedEvents.forEach { event in
      sendEvent(withName: "LiveActivityTokenUpdated", body: event)
    }
  }

  override func stopObserving() {
    tokenEventQueue.sync {
      hasEventListeners = false
    }
    logger.log("Live Activity token listener stopped")
  }

  private func tokenFingerprint(_ token: String) -> String {
    guard token.count > 12 else { return "length=\(token.count)" }
    return "\(token.prefix(6))…\(token.suffix(6)) length=\(token.count)"
  }

  private func makeState(_ params: ActivityStateParams) -> DeliveryAttributes.ContentState {
    DeliveryAttributes.ContentState(
      schemaVersion: params.schemaVersion,
      status: params.status.uppercased(),
      estimatedArrivalEpoch: params.estimatedArrivalEpoch,
      etaUpdatedAtEpoch: params.etaUpdatedAtEpoch,
      riderName: params.riderName,
      riderPhone: params.riderPhone,
      language: ["en", "ar", "he"].contains(params.language) ? params.language : "en"
    )
  }

  @available(iOS 16.2, *)
  private func observePushTokens(for activity: Activity<DeliveryAttributes>) {
    tokenTasks[activity.id]?.cancel()
    logger.log(
      "Starting push-token observation activity=\(activity.id, privacy: .public) order=\(activity.attributes.orderId, privacy: .public)"
    )
    tokenTasks[activity.id] = Task { [weak self] in
      for await tokenData in activity.pushTokenUpdates {
        guard !Task.isCancelled else { return }
        let token = tokenData.map { String(format: "%02x", $0) }.joined()
        guard let self else { return }
        let event = [
          "activityId": activity.id,
          "orderId": activity.attributes.orderId,
          "pushToken": token,
        ]
        self.logger.log(
          "ActivityKit push token received activity=\(activity.id, privacy: .public) token=\(self.tokenFingerprint(token), privacy: .public)"
        )

        let shouldEmit = self.tokenEventQueue.sync {
          if !self.hasEventListeners {
            self.pendingTokenEvents.removeAll {
              $0["activityId"] == activity.id
            }
            self.pendingTokenEvents.append(event)
          }
          return self.hasEventListeners
        }

        if shouldEmit {
          await MainActor.run {
            self.logger.log(
              "Emitting LiveActivityTokenUpdated activity=\(activity.id, privacy: .public)"
            )
            self.sendEvent(withName: "LiveActivityTokenUpdated", body: event)
          }
        } else {
          self.logger.log(
            "Queued LiveActivityTokenUpdated until JS listener starts activity=\(activity.id, privacy: .public)"
          )
        }
      }
      self?.logger.log(
        "Push-token observation ended activity=\(activity.id, privacy: .public)"
      )
    }
  }

  // MARK: - areLiveActivitiesEnabled
  @objc
  func areLiveActivitiesEnabled(_ resolve: @escaping RCTPromiseResolveBlock,
                                rejecter reject: @escaping RCTPromiseRejectBlock) {
    if #available(iOS 16.2, *) {
      resolve(ActivityAuthorizationInfo().areActivitiesEnabled)
    } else {
      resolve(false)
    }
  }

  // MARK: - startLiveActivity
  // JS will call: ActivityController.startLiveActivity(JSON.stringify(params))
  @objc
  func startLiveActivity(_ rawData: String,
                         resolver resolve: @escaping RCTPromiseResolveBlock,
                         rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
      do {
        guard #available(iOS 16.2, *) else {
          reject("ACTIVITY_UNAVAILABLE", "Live activities are not available on this system.", nil)
          return
        }

        // decode input
        guard let jsonData = rawData.data(using: .utf8) else {
          throw ActivityControllerError.unexpected("Invalid rawData string")
        }

        let params: StartParams
        do {
          params = try JSONDecoder().decode(StartParams.self, from: jsonData)
        } catch {
          throw ActivityControllerError.unexpected("Failed to decode start params: \(error.localizedDescription)")
        }
        logger.log(
          "startLiveActivity decoded order=\(params.orderId, privacy: .public) displayOrder=\(params.displayOrderId, privacy: .public)"
        )

        // This product intentionally supports one active delivery at a time.
        if !Activity<DeliveryAttributes>.activities.isEmpty {
          logger.log(
            "startLiveActivity returning existing activity id=\(Activity<DeliveryAttributes>.activities.first?.id ?? "", privacy: .public)"
          )
          resolve([
            "activityId": Activity<DeliveryAttributes>.activities.first?.id ?? "",
            "pushToken": "",
            "alreadyRunning": true,
          ])
          return
        }

        // ensure authorization
        guard ActivityAuthorizationInfo().areActivitiesEnabled else {
          logger.error("Live Activities are disabled by system authorization")
          reject("ACTIVITY_NOT_AUTHORIZED", "Live activities are not authorized", nil)
          return
        }

        let attributes = DeliveryAttributes(
          orderId: params.orderId,
          displayOrderId: params.displayOrderId
        )
        let contentState = makeState(params.state)

        // request activity with push token
        let activity = try Activity<DeliveryAttributes>.request(
          attributes: attributes,
          contentState: contentState,
          pushType: .token
        )

        observePushTokens(for: activity)
        let tokenString = activity.pushToken?.map { String(format: "%02x", $0) }.joined() ?? ""
        logger.log(
          "ActivityKit activity created id=\(activity.id, privacy: .public) order=\(params.orderId, privacy: .public) initialToken=\(tokenFingerprint(tokenString), privacy: .public)"
        )

        let result: [String: Any] = [
          "activityId": activity.id,
          "pushToken": tokenString
        ]

        resolve(result)

      } catch let err as ActivityControllerError {
        logger.error("startLiveActivity error: \(err.reason)")
        reject("START_LIVE_ACTIVITY_ERROR", err.reason, err)
      } catch {
        logger.error("startLiveActivity unexpected: \(error.localizedDescription)")
        reject("START_LIVE_ACTIVITY_ERROR", error.localizedDescription, error)
      }
    }
  }

  // MARK: - updateLiveActivity
  @objc
  func updateLiveActivity(_ rawData: String,
                          resolver resolve: @escaping RCTPromiseResolveBlock,
                          rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
      do {
        guard #available(iOS 16.2, *) else {
          reject("ACTIVITY_UNAVAILABLE", "Live activities are not available on this system.", nil)
          return
        }

        guard let jsonData = rawData.data(using: .utf8) else {
          throw ActivityControllerError.unexpected("Invalid rawData string")
        }

        let params: UpdateParams
        do {
          params = try JSONDecoder().decode(UpdateParams.self, from: jsonData)
        } catch {
          throw ActivityControllerError.unexpected("Failed to decode update params: \(error.localizedDescription)")
        }

        guard let activity = Activity<DeliveryAttributes>.activities.first(where: {
          $0.attributes.orderId == params.orderId
        }) else {
          reject("ACTIVITY_NOT_FOUND", "No live activity is running", nil)
          return
        }

        let updatedState = makeState(params.state)
        if params.terminal == true {
          await activity.end(
            using: updatedState,
            dismissalPolicy: .after(Date().addingTimeInterval(30 * 60))
          )
          tokenTasks[activity.id]?.cancel()
          tokenTasks.removeValue(forKey: activity.id)
        } else {
          await activity.update(using: updatedState)
        }
        resolve(nil)
      } catch let err as ActivityControllerError {
        logger.error("updateLiveActivity error: \(err.reason)")
        reject("UPDATE_LIVE_ACTIVITY_ERROR", err.reason, err)
      } catch {
        logger.error("updateLiveActivity unexpected: \(error.localizedDescription)")
        reject("UPDATE_LIVE_ACTIVITY_ERROR", error.localizedDescription, error)
      }
    }
  }

  // MARK: - stopLiveActivity
  @objc
  func stopLiveActivity(_ resolve: @escaping RCTPromiseResolveBlock,
                        rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
      guard #available(iOS 16.2, *) else {
        reject("ACTIVITY_UNAVAILABLE", "Live activities are not available on this system.", nil)
        return
      }

      guard let activity = Activity<DeliveryAttributes>.activities.first else {
        reject("ACTIVITY_NOT_FOUND", "No live activity is running", nil)
        return
      }

      await activity.end(dismissalPolicy: .immediate)
      tokenTasks[activity.id]?.cancel()
      tokenTasks.removeValue(forKey: activity.id)
      resolve(nil)
    }
  }

  // MARK: - isLiveActivityRunning
  @objc
  func isLiveActivityRunning(_ resolve: @escaping RCTPromiseResolveBlock,
                             rejecter reject: @escaping RCTPromiseRejectBlock) {
    if #available(iOS 16.2, *) {
      resolve(!Activity<DeliveryAttributes>.activities.isEmpty)
    } else {
      resolve(false)
    }
  }

  // MARK: - saveImageToAppGroup
  // JS: ActivityController.saveImageToAppGroup(url)
  @objc
  func saveImageToAppGroup(_ imageUrl: String,
                          appGroupId: String,
                          resolver resolve: @escaping RCTPromiseResolveBlock,
                          rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
      do {
        logger.log("saveImageToAppGroup start: \(imageUrl)")

        guard let url = URL(string: imageUrl) else {
          throw ActivityControllerError.unexpected("Invalid image URL: \(imageUrl)")
        }

        // let appGroupId = "group.com.enatega.customerapp"
        guard let containerUrl = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId) else {
          throw ActivityControllerError.unexpected("Unable to access App Group container for ID: \(appGroupId)")
        }

        let imagesFolder = containerUrl.appendingPathComponent("WidgetImages", isDirectory: true)
        try? FileManager.default.createDirectory(at: imagesFolder, withIntermediateDirectories: true)

        let fileUrl = imagesFolder.appendingPathComponent(url.lastPathComponent)

        if FileManager.default.fileExists(atPath: fileUrl.path) {
          logger.log("file exists at \(fileUrl.path)")
          resolve(fileUrl.path)
          return
        }

        logger.log("downloading \(url.absoluteString)")
        let (data, response) = try await URLSession.shared.data(from: url)

        guard let httpResponse = response as? HTTPURLResponse else {
          throw ActivityControllerError.unexpected("Invalid response type while downloading image")
        }

        guard httpResponse.statusCode == 200 else {
          throw ActivityControllerError.unexpected("Failed to download image: HTTP \(httpResponse.statusCode)")
        }

        try data.write(to: fileUrl, options: .atomic)
        logger.log("saved image to \(fileUrl.path)")
        resolve(fileUrl.path)
      } catch let err as ActivityControllerError {
        logger.error("saveImageToAppGroup error: \(err.reason)")
        reject("SAVE_IMAGE_ERROR", err.reason, err)
      } catch {
        logger.error("saveImageToAppGroup unexpected: \(error.localizedDescription)")
        reject("SAVE_IMAGE_ERROR", error.localizedDescription, error)
      }
    }
  }

  // MARK: - cleanAppGroupImages
  @objc
  func cleanAppGroupImages(_ maxAgeHours: Double,
                           appGroupId: String,
                           resolver resolve: @escaping RCTPromiseResolveBlock,
                           rejecter reject: @escaping RCTPromiseRejectBlock) {
    Task {
        // let appGroupId = "group.com.enatega.customerapp"
        guard let containerURL = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroupId) else {
          logger.error("Unable to access App Group container.")
          resolve(nil)
          return
        }

        let imagesFolder = containerURL.appendingPathComponent("WidgetImages", isDirectory: true)
        guard FileManager.default.fileExists(atPath: imagesFolder.path) else {
          logger.log("No Images folder — skipping cleanup.")
          resolve(nil)
          return
        }

        let expirySeconds = maxAgeHours * 3600
        let now = Date()
        let fm = FileManager.default

        guard let files = try? fm.contentsOfDirectory(at: imagesFolder, includingPropertiesForKeys: [.contentModificationDateKey], options: [.skipsHiddenFiles]) else {
          logger.error("Could not list contents of Images folder.")
          resolve(nil)
          return
        }

        for file in files {
          let fileName = file.lastPathComponent
          do {
            let attrs = try fm.attributesOfItem(atPath: file.path)
            if let modDate = attrs[.modificationDate] as? Date {
              let ageSeconds = now.timeIntervalSince(modDate)
              if ageSeconds > expirySeconds {
                logger.log("Removing expired image: \(fileName)")
                try? fm.removeItem(at: file)
              } else {
                logger.log("Keeping recent image: \(fileName)")
              }
            } else {
              logger.warning("Could not read modification date for: \(fileName)")
            }
          } catch {
            logger.error("Failed to process file \(fileName): \(error.localizedDescription)")
          }
        }

        logger.log("Image cleanup complete.")
        resolve(nil)
    }
  }
}
