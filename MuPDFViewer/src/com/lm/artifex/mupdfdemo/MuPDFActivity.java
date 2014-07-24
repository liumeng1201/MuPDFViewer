package com.lm.artifex.mupdfdemo;

import java.io.InputStream;
import java.util.concurrent.Executor;

import com.artifex.mupdfdemo.MuPDFCore;
import com.lm.artifex.mupdfdemo.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ViewAnimator;

class ThreadPerTaskExecutor implements Executor {
	public void execute(Runnable r) {
		new Thread(r).start();
	}
}

public class MuPDFActivity extends Activity implements
		FilePicker.FilePickerSupport {
	enum TopBarMode {
		Main, Search, Annot, Delete, More, Accept
	};
	enum AcceptMode {
		Highlight, Underline, StrikeOut, Ink, CopyText
	};

	private final int FILEPICK_REQUEST = 2;
	private MuPDFCore core;
	private String mFileName;
	private MuPDFReaderView mDocView;
	private View mButtonsView;
	private boolean mButtonsVisible;
	private EditText mPasswordView;
	private TextView mFilenameView;
	private SeekBar mPageSlider;
	private int mPageSliderRes;
	private TextView mPageNumberView;
	private TextView mInfoView;
	private ViewAnimator mTopBarSwitcher;
	private TopBarMode mTopBarMode = TopBarMode.Main;
	private AlertDialog.Builder mAlertBuilder;
	private boolean mLinkHighlight = false;
	private boolean mAlertsActive = false;
	private boolean mReflow = false;
	private AsyncTask<Void, Void, MuPDFAlert> mAlertTask;
	private AlertDialog mAlertDialog;
	private FilePicker mFilePicker;
	private boolean firstView;

	public void createAlertWaiter() {
		mAlertsActive = true;
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
		if (mAlertDialog != null) {
			mAlertDialog.cancel();
			mAlertDialog = null;
		}
		mAlertTask = new AsyncTask<Void, Void, MuPDFAlert>() {
			@Override
			protected MuPDFAlert doInBackground(Void... arg0) {
				if (!mAlertsActive)
					return null;
				return core.waitForAlert();
			}

			@Override
			protected void onPostExecute(final MuPDFAlert result) {
				// core.waitForAlert may return null when shutting down
				if (result == null)
					return;
				final MuPDFAlert.ButtonPressed pressed[] = new MuPDFAlert.ButtonPressed[3];
				for (int i = 0; i < 3; i++)
					pressed[i] = MuPDFAlert.ButtonPressed.None;
				DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						mAlertDialog = null;
						if (mAlertsActive) {
							int index = 0;
							switch (which) {
							case AlertDialog.BUTTON1:
								index = 0;
								break;
							case AlertDialog.BUTTON2:
								index = 1;
								break;
							case AlertDialog.BUTTON3:
								index = 2;
								break;
							}
							result.buttonPressed = pressed[index];
							core.replyToAlert(result);
							createAlertWaiter();
						}
					}
				};
				mAlertDialog = mAlertBuilder.create();
				mAlertDialog.setTitle(result.title);
				mAlertDialog.setMessage(result.message);
				switch (result.iconType) {
				case Error:
					break;
				case Warning:
					break;
				case Question:
					break;
				case Status:
					break;
				}
				switch (result.buttonGroupType) {
				case OkCancel:
					mAlertDialog.setButton(AlertDialog.BUTTON2,
							getString(R.string.cancel), listener);
					pressed[1] = MuPDFAlert.ButtonPressed.Cancel;
				case Ok:
					mAlertDialog.setButton(AlertDialog.BUTTON1,
							getString(R.string.okay), listener);
					pressed[0] = MuPDFAlert.ButtonPressed.Ok;
					break;
				case YesNoCancel:
					mAlertDialog.setButton(AlertDialog.BUTTON3,
							getString(R.string.cancel), listener);
					pressed[2] = MuPDFAlert.ButtonPressed.Cancel;
				case YesNo:
					mAlertDialog.setButton(AlertDialog.BUTTON1,
							getString(R.string.yes), listener);
					pressed[0] = MuPDFAlert.ButtonPressed.Yes;
					mAlertDialog.setButton(AlertDialog.BUTTON2,
							getString(R.string.no), listener);
					pressed[1] = MuPDFAlert.ButtonPressed.No;
					break;
				}
				mAlertDialog
						.setOnCancelListener(new DialogInterface.OnCancelListener() {
							public void onCancel(DialogInterface dialog) {
								mAlertDialog = null;
								if (mAlertsActive) {
									result.buttonPressed = MuPDFAlert.ButtonPressed.None;
									core.replyToAlert(result);
									createAlertWaiter();
								}
							}
						});
				mAlertDialog.show();
			}
		};
		mAlertTask.executeOnExecutor(new ThreadPerTaskExecutor());
	}

	public void destroyAlertWaiter() {
		mAlertsActive = false;
		if (mAlertDialog != null) {
			mAlertDialog.cancel();
			mAlertDialog = null;
		}
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
	}

	private MuPDFCore openFile(String path) {
		int lastSlashPos = path.lastIndexOf('/');
		mFileName = new String(lastSlashPos == -1 ? path
				: path.substring(lastSlashPos + 1));
		System.out.println("Trying to open " + path);
		try {
			core = new MuPDFCore(this, path);
			OutlineActivityData.set(null);
		} catch (Exception e) {
			System.out.println(e);
			return null;
		}
		return core;
	}

	private MuPDFCore openBuffer(byte buffer[]) {
		System.out.println("Trying to open byte buffer");
		try {
			core = new MuPDFCore(this, buffer);
			OutlineActivityData.set(null);
		} catch (Exception e) {
			System.out.println(e);
			return null;
		}
		return core;
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mAlertBuilder = new AlertDialog.Builder(this);
		if (core == null) {
			core = (MuPDFCore) getLastNonConfigurationInstance();
			if (savedInstanceState != null
					&& savedInstanceState.containsKey("FileName")) {
				mFileName = savedInstanceState.getString("FileName");
			}
		}
		if (core == null) {
			Intent intent = getIntent();
			firstView = intent.getBooleanExtra("firstView", false);
			byte buffer[] = null;
			if (Intent.ACTION_VIEW.equals(intent.getAction())) {
				Uri uri = intent.getData();
				if (uri.toString().startsWith("content://")) {
					Cursor cursor = getContentResolver().query(uri,
							new String[] { "_data" }, null, null, null);
					if (cursor.moveToFirst()) {
						String str = cursor.getString(0);
						String reason = null;
						if (str == null) {
							try {
								InputStream is = getContentResolver()
										.openInputStream(uri);
								int len = is.available();
								buffer = new byte[len];
								is.read(buffer, 0, len);
								is.close();
							} catch (java.lang.OutOfMemoryError e) {
								System.out
										.println("Out of memory during buffer reading");
								reason = e.toString();
							} catch (Exception e) {
								reason = e.toString();
							}
							if (reason != null) {
								buffer = null;
								Resources res = getResources();
								AlertDialog alert = mAlertBuilder.create();
								setTitle(String
										.format(res
												.getString(R.string.cannot_open_document_Reason),
												reason));
								alert.setButton(AlertDialog.BUTTON_POSITIVE,
										getString(R.string.dismiss),
										new DialogInterface.OnClickListener() {
											public void onClick(
													DialogInterface dialog,
													int which) {
												finish();
											}
										});
								alert.show();
								return;
							}
						} else {
							uri = Uri.parse(str);
						}
					}
				}
				if (buffer != null) {
					core = openBuffer(buffer);
				} else {
					core = openFile(Uri.decode(uri.getEncodedPath()));
				}
				SearchTaskResult.set(null);
			}
			if (core != null && core.needsPassword()) {
				requestPassword(savedInstanceState);
				return;
			}
			if (core != null && core.countPages() == 0) {
				core = null;
			}
		}
		if (core == null) {
			AlertDialog alert = mAlertBuilder.create();
			alert.setTitle(R.string.cannot_open_document);
			alert.setButton(AlertDialog.BUTTON_POSITIVE,
					getString(R.string.dismiss),
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							finish();
						}
					});
			alert.show();
			return;
		}
		createUI(savedInstanceState);
	}

	public void requestPassword(final Bundle savedInstanceState) {
		mPasswordView = new EditText(this);
		mPasswordView.setInputType(EditorInfo.TYPE_TEXT_VARIATION_PASSWORD);
		mPasswordView
				.setTransformationMethod(new PasswordTransformationMethod());
		AlertDialog alert = mAlertBuilder.create();
		alert.setTitle(R.string.enter_password);
		alert.setView(mPasswordView);
		alert.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.okay),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						if (core.authenticatePassword(mPasswordView.getText()
								.toString())) {
							createUI(savedInstanceState);
						} else {
							requestPassword(savedInstanceState);
						}
					}
				});
		alert.setButton(AlertDialog.BUTTON_NEGATIVE,
				getString(R.string.cancel),
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});
		alert.show();
	}

	public void createUI(Bundle savedInstanceState) {
		if (core == null)
			return;

		mDocView = new MuPDFReaderView(this) {
			@Override
			protected void onMoveToChild(int i) {
				if (core == null)
					return;
				mPageNumberView.setText(String.format("%d / %d", i + 1,
						core.countPages()));
				mPageSlider.setMax((core.countPages() - 1) * mPageSliderRes);
				mPageSlider.setProgress(i * mPageSliderRes);
				super.onMoveToChild(i);
			}

			@Override
			protected void onTapMainDocArea() {
				if (!mButtonsVisible) {
					showButtons();
				} else {
					if (mTopBarMode == TopBarMode.Main)
						hideButtons();
				}
			}

			@Override
			protected void onDocMotion() {
				hideButtons();
			}

			@Override
			protected void onHit(Hit item) {
				switch (mTopBarMode) {
				case Annot:
					if (item == Hit.Annotation) {
						showButtons();
						mTopBarMode = TopBarMode.Delete;
						mTopBarSwitcher
								.setDisplayedChild(mTopBarMode.ordinal());
					}
					break;
				case Delete:
					mTopBarMode = TopBarMode.Annot;
					mTopBarSwitcher.setDisplayedChild(mTopBarMode.ordinal());
				default:
					MuPDFView pageView = (MuPDFView) mDocView
							.getDisplayedView();
					if (pageView != null)
						pageView.deselectAnnotation();
					break;
				}
			}
		};
		mDocView.setAdapter(new MuPDFPageAdapter(this, this, core));

		makeButtonsView();

		// Set up the page slider
		int smax = Math.max(core.countPages() - 1, 1);
		mPageSliderRes = ((10 + smax - 1) / smax) * 2;

		// Set the file-name text
		mFilenameView.setText(mFileName);

		// Activate the seekbar
		mPageSlider
				.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
					public void onStopTrackingTouch(SeekBar seekBar) {
						mDocView.setDisplayedViewIndex((seekBar.getProgress() + mPageSliderRes / 2)
								/ mPageSliderRes);
					}

					public void onStartTrackingTouch(SeekBar seekBar) {
					}

					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						updatePageNumView((progress + mPageSliderRes / 2)
								/ mPageSliderRes);
					}
				});

		// Reenstate last state if it was recorded
		SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
		// ��һ�ν����ͷ��ʼ�Ķ�
		if (firstView) {
			mDocView.setDisplayedViewIndex(0);
			firstView = false;
		} else {
			mDocView.setDisplayedViewIndex(prefs.getInt("page" + mFileName, 0));
		}

		if (savedInstanceState == null
				|| !savedInstanceState.getBoolean("ButtonsHidden", false))
			showButtons();

		if (savedInstanceState != null
				&& savedInstanceState.getBoolean("ReflowMode", false))
			reflowModeSet(true);

		// Stick the document view and the buttons overlay into a parent view
		RelativeLayout layout = new RelativeLayout(this);
		layout.addView(mDocView);
		layout.addView(mButtonsView);
		setContentView(layout);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode) {
		case FILEPICK_REQUEST:
			if (mFilePicker != null && resultCode == RESULT_OK)
				mFilePicker.onPick(data.getData());
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	public Object onRetainNonConfigurationInstance() {
		MuPDFCore mycore = core;
		core = null;
		return mycore;
	}

	private void reflowModeSet(boolean reflow) {
		mReflow = reflow;
		mDocView.setAdapter(mReflow ? new MuPDFReflowAdapter(this, core)
				: new MuPDFPageAdapter(this, this, core));
		if (reflow)
			setLinkHighlight(false);
		mDocView.refresh(mReflow);
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mFileName != null && mDocView != null) {
			outState.putString("FileName", mFileName);
			SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor edit = prefs.edit();
			edit.putInt("page" + mFileName, mDocView.getDisplayedViewIndex());
			edit.commit();
		}

		if (!mButtonsVisible)
			outState.putBoolean("ButtonsHidden", true);

//		if (mTopBarMode == TopBarMode.Search)
//			outState.putBoolean("SearchMode", true);

		if (mReflow)
			outState.putBoolean("ReflowMode", true);
	}

	@Override
	protected void onPause() {
		super.onPause();

		if (mFileName != null && mDocView != null) {
			SharedPreferences prefs = getPreferences(Context.MODE_PRIVATE);
			SharedPreferences.Editor edit = prefs.edit();
			edit.putInt("page" + mFileName, mDocView.getDisplayedViewIndex());
			edit.commit();
		}
	}

	public void onDestroy() {
		if (mDocView != null) {
			mDocView.applyToChildren(new ReaderView.ViewMapper() {
				void applyToView(View view) {
					((MuPDFView) view).releaseBitmaps();
				}
			});
		}
		if (core != null)
			core.onDestroy();
		if (mAlertTask != null) {
			mAlertTask.cancel(true);
			mAlertTask = null;
		}
		core = null;
		
		super.onDestroy();
	}

	private void setLinkHighlight(boolean highlight) {
		mLinkHighlight = highlight;
		// Inform pages of the change.
		mDocView.setLinksEnabled(highlight);
	}

	private void showButtons() {
		if (core == null)
			return;
		if (!mButtonsVisible) {
			mButtonsVisible = true;
			// Update page number text and slider
			int index = mDocView.getDisplayedViewIndex();
			updatePageNumView(index);
			mPageSlider.setMax((core.countPages() - 1) * mPageSliderRes);
			mPageSlider.setProgress(index * mPageSliderRes);

			Animation anim = new TranslateAnimation(0, 0,
					-mTopBarSwitcher.getHeight(), 0);
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
					mTopBarSwitcher.setVisibility(View.VISIBLE);
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
				}
			});
			mTopBarSwitcher.startAnimation(anim);

			anim = new TranslateAnimation(0, 0, mPageSlider.getHeight(), 0);
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
					mPageSlider.setVisibility(View.VISIBLE);
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
					mPageNumberView.setVisibility(View.VISIBLE);
				}
			});
			mPageSlider.startAnimation(anim);
		}
	}

	private void hideButtons() {
		if (mButtonsVisible) {
			mButtonsVisible = false;

			Animation anim = new TranslateAnimation(0, 0, 0,
					-mTopBarSwitcher.getHeight());
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
					mTopBarSwitcher.setVisibility(View.INVISIBLE);
				}
			});
			mTopBarSwitcher.startAnimation(anim);

			anim = new TranslateAnimation(0, 0, 0, mPageSlider.getHeight());
			anim.setDuration(200);
			anim.setAnimationListener(new Animation.AnimationListener() {
				public void onAnimationStart(Animation animation) {
					mPageNumberView.setVisibility(View.INVISIBLE);
				}

				public void onAnimationRepeat(Animation animation) {
				}

				public void onAnimationEnd(Animation animation) {
					mPageSlider.setVisibility(View.INVISIBLE);
				}
			});
			mPageSlider.startAnimation(anim);
		}
	}

	private void updatePageNumView(int index) {
		if (core == null)
			return;
		mPageNumberView.setText(String.format("%d / %d", index + 1,
				core.countPages()));
	}

	private void makeButtonsView() {
		mButtonsView = getLayoutInflater().inflate(R.layout.buttons, null);
		mFilenameView = (TextView) mButtonsView.findViewById(R.id.docNameText);
		mPageSlider = (SeekBar) mButtonsView.findViewById(R.id.pageSlider);
		mPageNumberView = (TextView) mButtonsView.findViewById(R.id.pageNumber);
		mInfoView = (TextView) mButtonsView.findViewById(R.id.info);
		mTopBarSwitcher = (ViewAnimator) mButtonsView
				.findViewById(R.id.switcher);
		mTopBarSwitcher.setVisibility(View.INVISIBLE);
		mPageNumberView.setVisibility(View.INVISIBLE);
		mInfoView.setVisibility(View.INVISIBLE);
		mPageSlider.setVisibility(View.INVISIBLE);
	}

	@Override
	protected void onStart() {
		if (core != null) {
			core.startAlerts();
			createAlertWaiter();
		}
		super.onStart();
	}

	@Override
	protected void onStop() {
		if (core != null) {
			destroyAlertWaiter();
			core.stopAlerts();
		}
		super.onStop();
	}

	@Override
	public void onBackPressed() {
		super.onBackPressed();
	}

	@Override
	public void performPickFor(FilePicker picker) {
		mFilePicker = picker;
		Intent intent = new Intent(this, ChoosePDFActivityBackup.class);
		startActivityForResult(intent, FILEPICK_REQUEST);
	}
}
