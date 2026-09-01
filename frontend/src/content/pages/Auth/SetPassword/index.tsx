import { useContext } from 'react';
import * as Yup from 'yup';
import { Formik } from 'formik';
import {
  Link as RouterLink,
  useNavigate,
  useSearchParams
} from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CircularProgress,
  Container,
  Link,
  styled,
  TextField,
  Typography
} from '@mui/material';
import { Helmet } from 'react-helmet-async';
import { useTranslation } from 'react-i18next';
import Logo from 'src/components/LogoSign';
import useRefMounted from 'src/hooks/useRefMounted';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import api from '../../../../utils/api';

const MainContent = styled(Box)(
  () => `
    height: 100%;
    display: flex;
    flex: 1;
    flex-direction: column;
    align-items: center;
    justify-content: center;
`
);

/**
 * Landing page for the welcome / set-password email link. Reads the one-time token from the URL,
 * lets the user choose their own password, and posts it to POST /auth/set-password.
 */
function SetPassword() {
  const { t }: { t: any } = useTranslation();
  const isMountedRef = useRefMounted();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token');
  const { showSnackBar } = useContext(CustomSnackBarContext);

  return (
    <>
      <Helmet>
        <title>{t('Set your password')}</title>
      </Helmet>
      <MainContent>
        <Container maxWidth="sm">
          <Logo />
          <Card sx={{ mt: 3, p: 4 }}>
            <Box>
              <Typography variant="h2" sx={{ mb: 1 }}>
                {t('Set your password')}
              </Typography>
              <Typography
                variant="h4"
                color="text.secondary"
                fontWeight="normal"
                sx={{ mb: 3 }}
              >
                {t('Choose a password to activate your account.')}
              </Typography>
            </Box>
            {!token ? (
              <Alert severity="error">{t('Invalid or missing link.')}</Alert>
            ) : (
              <Formik
                initialValues={{
                  password: '',
                  confirmPassword: '',
                  submit: null
                }}
                validationSchema={Yup.object().shape({
                  password: Yup.string()
                    .min(12)
                    .max(128)
                    .required(t('required_password')),
                  confirmPassword: Yup.string()
                    .oneOf([Yup.ref('password')], t('Passwords must match'))
                    .required(t('required_password'))
                })}
                onSubmit={async (values, { setSubmitting }) => {
                  setSubmitting(true);
                  try {
                    await api.post('auth/set-password', {
                      token,
                      newPassword: values.password
                    });
                    showSnackBar(
                      t('Password set successfully. Please log in.'),
                      'success'
                    );
                    navigate('/account/login');
                  } catch (err: any) {
                    let message = t("The operation didn't succeed");
                    try {
                      message = JSON.parse(err.message).message;
                    } catch {}
                    showSnackBar(message, 'error');
                  } finally {
                    if (isMountedRef.current) setSubmitting(false);
                  }
                }}
              >
                {({
                  errors,
                  handleBlur,
                  handleChange,
                  handleSubmit,
                  isSubmitting,
                  touched,
                  values
                }) => (
                  <form noValidate onSubmit={handleSubmit}>
                    <TextField
                      error={Boolean(touched.password && errors.password)}
                      fullWidth
                      margin="normal"
                      helperText={touched.password && errors.password}
                      label={t('password')}
                      name="password"
                      type="password"
                      onBlur={handleBlur}
                      onChange={handleChange}
                      value={values.password}
                      variant="outlined"
                    />
                    <TextField
                      error={Boolean(
                        touched.confirmPassword && errors.confirmPassword
                      )}
                      fullWidth
                      margin="normal"
                      helperText={
                        touched.confirmPassword && errors.confirmPassword
                      }
                      label={t('Confirm password')}
                      name="confirmPassword"
                      type="password"
                      onBlur={handleBlur}
                      onChange={handleChange}
                      value={values.confirmPassword}
                      variant="outlined"
                    />
                    <Button
                      sx={{ mt: 3 }}
                      disabled={isSubmitting}
                      type="submit"
                      fullWidth
                      size="large"
                      variant="contained"
                      startIcon={
                        isSubmitting ? <CircularProgress size="1rem" /> : null
                      }
                    >
                      {t('Set your password')}
                    </Button>
                  </form>
                )}
              </Formik>
            )}
          </Card>
          <Box mt={3} textAlign="right">
            <Link component={RouterLink} to="/account/login">
              <b>{t('login')}</b>
            </Link>
          </Box>
        </Container>
      </MainContent>
    </>
  );
}

export default SetPassword;
