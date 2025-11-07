import { requireNativeView } from 'expo';
import * as React from 'react';

import { ActivityControllerViewProps } from './ActivityController.types';

const NativeView: React.ComponentType<ActivityControllerViewProps> =
  requireNativeView('ActivityController');

export default function ActivityControllerView(props: ActivityControllerViewProps) {
  return <NativeView {...props} />;
}
