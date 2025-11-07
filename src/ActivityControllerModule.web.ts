import { registerWebModule, NativeModule } from 'expo';

import { ChangeEventPayload } from './ActivityController.types';

type ActivityControllerModuleEvents = {
  onChange: (params: ChangeEventPayload) => void;
}

class ActivityControllerModule extends NativeModule<ActivityControllerModuleEvents> {
  PI = Math.PI;
  async setValueAsync(value: string): Promise<void> {
    this.emit('onChange', { value });
  }
  hello() {
    return 'Hello world! 👋';
  }
};

export default registerWebModule(ActivityControllerModule, 'ActivityControllerModule');
