import api from '@/lib/api';

export const avatarApi = {
  upload(file: File) {
    const form = new FormData();
    form.append('file', file);
    return api.post('/users/me/avatar', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  setPreset(presetId: string) {
    return api.put(`/users/me/avatar/preset/${presetId}`);
  },

  delete() {
    return api.delete('/users/me/avatar');
  },

  getPresets() {
    return api.get<string[]>('/users/avatar/presets');
  },
};