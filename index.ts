import { NativeModules } from 'react-native';

const { ActivityControllerModuleBridge } = NativeModules;

export default {
  areLiveActivitiesEnabled: () => ActivityControllerModuleBridge.areLiveActivitiesEnabled(),
  startLiveActivity: (rawData: string) => ActivityControllerModuleBridge.startLiveActivity(rawData),
  updateLiveActivity: (rawData: string) => ActivityControllerModuleBridge.updateLiveActivity(rawData),
  stopLiveActivity: () => ActivityControllerModuleBridge.stopLiveActivity(),
  isLiveActivityRunning: () => ActivityControllerModuleBridge.isLiveActivityRunning(),
  saveImageToAppGroup: (url: string) => ActivityControllerModuleBridge.saveImageToAppGroup(url),
  cleanAppGroupImages: (maxAgeHours: number) => ActivityControllerModuleBridge.cleanAppGroupImages(maxAgeHours)
};
