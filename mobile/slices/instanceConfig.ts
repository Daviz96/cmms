import type { PayloadAction } from '@reduxjs/toolkit';
import { createSlice } from '@reduxjs/toolkit';
import type { AppThunk } from '../store';
import api, { authHeader } from '../utils/api';
import { getApiUrl } from '../config';
import { revertAll } from '../utils/redux';

interface InstanceConfigState {
  ldapEnabled: boolean;
  loading: boolean;
}

const initialState: InstanceConfigState = {
  ldapEnabled: false,
  loading: false
};

const slice = createSlice({
  name: 'instanceConfig',
  initialState,
  extraReducers: (builder) => builder.addCase(revertAll, () => initialState),
  reducers: {
    setInstanceConfig(
      state: InstanceConfigState,
      action: PayloadAction<{ ldapEnabled: boolean }>
    ) {
      const { ldapEnabled } = action.payload;
      state.ldapEnabled = ldapEnabled;
    },
    setLoading(
      state: InstanceConfigState,
      action: PayloadAction<{ loading: boolean }>
    ) {
      const { loading } = action.payload;
      state.loading = loading;
    }
  }
});

export const reducer = slice.reducer;

export const getInstanceConfig = (): AppThunk => async (dispatch) => {
  try {
    dispatch(slice.actions.setLoading({ loading: true }));
    // MOD-015 (M-BUG-1): skip the fetch when no API server is configured yet
    // (e.g. a dev build before the custom server is set). getApiUrl() returns ''
    // in that case, so calling the endpoint would fail with a network error and
    // surface a spurious "Failed to fetch instance config" toast at startup.
    const apiUrl = await getApiUrl();
    if (!apiUrl) {
      return;
    }
    const response = await api.get<{ ldapEnabled: boolean }>(
      'instance-config',
      {
        headers: await authHeader(true)
      }
    );
    dispatch(
      slice.actions.setInstanceConfig({ ldapEnabled: response.ldapEnabled })
    );
  } catch (error) {
    console.error('Failed to fetch instance config:', error);
  } finally {
    dispatch(slice.actions.setLoading({ loading: false }));
  }
};

export default slice;
