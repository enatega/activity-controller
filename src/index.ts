import { TurboModuleRegistry } from "react-native";

export interface ActivityControllerSpec {
  readonly areLiveActivitiesEnabled: boolean;

  startLiveActivity(rawData: string): Promise<{
    activityId: string;
    pushToken: string;
  }>;

  updateLiveActivity(rawData: string): Promise<void>;
  stopLiveActivity(): Promise<void>;

  isLiveActivityRunning(): boolean;

  saveImageToAppGroup(imageUrl: string): Promise<string>;
  cleanAppGroupImages(maxAgeHours: number): Promise<void>;
}

const ActivityController =
  TurboModuleRegistry.getEnforcing<ActivityControllerSpec>(
    "ActivityController"
  );

export default ActivityController;
