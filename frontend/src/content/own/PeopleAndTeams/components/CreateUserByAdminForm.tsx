import { Formik } from 'formik';
import * as Yup from 'yup';
import {
  Button,
  CircularProgress,
  Grid,
  TextField,
  Typography
} from '@mui/material';
import { useContext } from 'react';
import { useTranslation } from 'react-i18next';
import { useDispatch } from '../../../../store';
import { createUserByAdmin } from '../../../../slices/user';
import { CustomSnackBarContext } from '../../../../contexts/CustomSnackBarContext';
import { phoneRegExp } from '../../../../utils/validators';

/**
 * Admin creates a user manually. No password field: the backend creates the account and sends a
 * welcome email with a one-time link where the user sets their own password.
 */
export default function CreateUserByAdminForm({
  roleId,
  onClose,
  onRefreshUsers
}: {
  roleId: number;
  onClose: () => void;
  onRefreshUsers: () => void;
}) {
  const { t }: { t: any } = useTranslation();
  const dispatch = useDispatch();
  const { showSnackBar } = useContext(CustomSnackBarContext);

  return (
    <Formik
      initialValues={{
        firstName: '',
        lastName: '',
        email: '',
        phone: '',
        submit: null
      }}
      validationSchema={Yup.object().shape({
        firstName: Yup.string().max(255).required(t('required_firstName')),
        lastName: Yup.string().max(255).required(t('required_lastName')),
        email: Yup.string()
          .email(t('invalid_email'))
          .max(255)
          .required(t('required_email')),
        phone: Yup.string().matches(phoneRegExp, t('invalid_phone'))
      })}
      onSubmit={async (values, { setSubmitting }) => {
        setSubmitting(true);
        try {
          await dispatch(
            createUserByAdmin({
              firstName: values.firstName,
              lastName: values.lastName,
              email: values.email,
              phone: values.phone,
              roleId
            })
          );
          showSnackBar(
            t('User created. A set-password email has been sent.'),
            'success'
          );
          onClose();
          onRefreshUsers();
        } catch (err: any) {
          let message = t("The operation didn't succeed");
          try {
            message = JSON.parse(err.message).message;
          } catch {}
          showSnackBar(message, 'error');
        } finally {
          setSubmitting(false);
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
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {t('The user will receive an email to set their own password.')}
          </Typography>
          <Grid container spacing={1}>
            <Grid item xs={12} lg={6}>
              <TextField
                error={Boolean(touched.firstName && errors.firstName)}
                fullWidth
                margin="normal"
                helperText={touched.firstName && errors.firstName}
                label={t('first_name')}
                name="firstName"
                onBlur={handleBlur}
                onChange={handleChange}
                value={values.firstName}
                variant="outlined"
              />
            </Grid>
            <Grid item xs={12} lg={6}>
              <TextField
                error={Boolean(touched.lastName && errors.lastName)}
                fullWidth
                margin="normal"
                helperText={touched.lastName && errors.lastName}
                label={t('last_name')}
                name="lastName"
                onBlur={handleBlur}
                onChange={handleChange}
                value={values.lastName}
                variant="outlined"
              />
            </Grid>
          </Grid>
          <TextField
            error={Boolean(touched.email && errors.email)}
            fullWidth
            margin="normal"
            helperText={touched.email && errors.email}
            label={t('email')}
            name="email"
            onBlur={handleBlur}
            onChange={handleChange}
            type="email"
            value={values.email}
            variant="outlined"
          />
          <TextField
            error={Boolean(touched.phone && errors.phone)}
            fullWidth
            margin="normal"
            helperText={touched.phone && errors.phone}
            label={t('phone')}
            name="phone"
            onBlur={handleBlur}
            onChange={handleChange}
            value={values.phone}
            variant="outlined"
          />
          <Button
            sx={{ mt: 3 }}
            color="primary"
            startIcon={isSubmitting ? <CircularProgress size="1rem" /> : null}
            disabled={isSubmitting}
            type="submit"
            fullWidth
            size="large"
            variant="contained"
          >
            {t('Create user')}
          </Button>
        </form>
      )}
    </Formik>
  );
}
