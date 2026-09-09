import { startDeploy, getDeployStatus } from '../lib/deploy.js';

export async function adminDeployLatest() {
  return startDeploy();
}

export async function adminDeployStatus() {
  return getDeployStatus();
}
