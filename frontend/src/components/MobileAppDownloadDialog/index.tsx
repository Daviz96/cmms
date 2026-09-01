import {
  Dialog,
  DialogContent,
  DialogTitle,
  Typography,
  Box,
  Button
} from '@mui/material';
import { useTranslation } from 'react-i18next';

interface MobileAppDownloadDialogProps {
  open: boolean;
  onClose: () => void;
}

// Self-hosted: distribute our own APK (served by Caddy at /download on the same origin),
// not the official Play Store / App Store apps (which cannot talk to a self-hosted backend).
const APK_DOWNLOAD_PATH = '/download/atlas-cmms.apk';

export default function MobileAppDownloadDialog({
  open,
  onClose
}: MobileAppDownloadDialogProps) {
  const { t }: { t: any } = useTranslation();

  const handleDownloadClick = () => {
    window.open(`${window.location.origin}${APK_DOWNLOAD_PATH}`, '_blank');
    onClose();
  };

  return (
    <Dialog fullWidth maxWidth="sm" open={open} onClose={onClose}>
      <DialogTitle sx={{ p: 3 }}>
        <Typography variant="h4" gutterBottom>
          {t('Download Mobile App')}
        </Typography>
      </DialogTitle>
      <DialogContent dividers sx={{ p: 3 }}>
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Typography variant="body1">
            {t(
              'Enhance your experience with our mobile app. Get instant notifications and manage your work orders on the go.'
            )}
          </Typography>
          <Button
            variant="contained"
            fullWidth
            size="large"
            onClick={handleDownloadClick}
            sx={{ py: 1.5, fontSize: '1rem', mt: 2 }}
          >
            {t('Download the app (APK)')}
          </Button>
        </Box>
      </DialogContent>
    </Dialog>
  );
}
